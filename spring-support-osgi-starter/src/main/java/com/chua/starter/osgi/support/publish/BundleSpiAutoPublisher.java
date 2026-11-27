// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.osgi.support.publish;

import com.chua.common.support.osgi.BundleLifecycleListener;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.osgi.properties.OsgiProperties;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 外部 bundle SPI 自动发布器。
 *
 * <p>监听 bundle 生命周期：bundle 启动（STARTED）时扫描其中的 SPI 实现
 * 并发布为 OSGi 服务；bundle 停止 / 卸载 / 更新时按符号名自动反注册，
 * 保证外部 jar 的服务随插件存亡。</p>
 *
 * <p>SPI 实现的发现顺序：</p>
 * <ol>
 *   <li>优先读取标准 SPI 索引 {@code META-INF/extensions/<接口全限定名>}，
 *       每行一个实现类全限定名；</li>
 *   <li>无索引时退回扫描 bundle 内全部 {@code .class}（带数量上限，防止大 jar
 *       拖慢框架事件线程），只保留标注 {@link Spi} 的可实例化类。</li>
 * </ol>
 *
 * <p>每个实现按其全部业务接口（跨类层级收集、排除框架接口，口径与
 * {@link OsgiSpiBeanPublisher} 一致，见 {@link SpiPublishSupport}）逐个发布；
 * 实例由 bundle 自身的 classloader 创建。无符号名的 bundle（纯 SSH 安装的裸 jar）
 * 无法按符号名反注册，跳过不发布。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class BundleSpiAutoPublisher implements BundleLifecycleListener {

    /**
     * 标准 SPI 索引目录。
     */
    private static final String SPI_INDEX_DIR = "META-INF/extensions";

    /**
     * Felix osgi 启动器。
     */
    private final FelixOsgiLauncher launcher;

    /**
     * 服务发布门面。
     */
    private final OsgiServicePublisher publisher;

    /**
     * 自动发布配置。
     */
    private final OsgiProperties.Spi options;

    /**
     * 已按 bundle 符号名发布的实例（用于停止 / 卸载时反注册）。
     */
    private final ConcurrentMap<String, List<Object>> publishedByBundle = new ConcurrentHashMap<>();

    /** 宿主销毁后拒绝异步到达的 bundle 事件。 */
    private boolean closed;

    /**
     * 宿主 ServiceProvider 入口桥：把 bundle 发布的 SPI 实现同步登记给宿主侧
     * 消费入口（NativeServices / ServiceProvider.of(type).getExtension），并在
     * bundle 停止 / 卸载 / 更新时同步摘除，避免宿主残留幽灵实现。
     */
    private final HostSpiRegistryBridge hostBridge = new HostSpiRegistryBridge();

    /**
     * 构造自动发布器。
     *
     * @param launcher  Felix osgi 启动器
     * @param publisher 服务发布门面
     * @param options   自动发布配置
     */
    public BundleSpiAutoPublisher(FelixOsgiLauncher launcher,
                                  OsgiServicePublisher publisher,
                                  OsgiProperties.Spi options) {
        this.launcher = launcher;
        this.publisher = publisher;
        this.options = options == null ? new OsgiProperties.Spi() : options;
    }

    /**
     * 注册框架级监听器，并补发布启动时已处于 ACTIVE 状态 bundle 中的 SPI 实现。
     */
    public void init() {
        launcher.addListener(this);
        if (!options.isEnabled() || publisher == null || !publisher.isActive()) {
            return;
        }
        BundleContext context = launcher.getFrameworkBundleContext();
        if (context == null) {
            return;
        }
        for (Bundle bundle : context.getBundles()) {
            if (bundle.getState() != Bundle.ACTIVE) {
                continue;
            }
            String symbolicName = bundle.getSymbolicName();
            if (symbolicName != null && !symbolicName.isBlank()) {
                publishBundle(symbolicName);
            }
        }
    }

    /**
     * 注销框架级监听器。
     */
    public synchronized void destroy() {
        closed = true;
        launcher.removeListener(this);
        for (String symbolicName : List.copyOf(publishedByBundle.keySet())) {
            unpublishBundle(symbolicName);
        }
        hostBridge.unregisterAll();
    }

    @Override
    public void onBundleInstalled(String symbolicName) {
        // 安装不发布：bundle 可能尚未启动，等 STARTED 再发布
    }

    @Override
    public void onBundleStarted(String symbolicName) {
        publishBundle(symbolicName);
    }

    @Override
    public void onBundleStopped(String symbolicName) {
        unpublishBundle(symbolicName);
    }

    @Override
    public void onBundleUpdated(String symbolicName, String newVersion) {
        // 更新后 bundle 若处于活动态会重新启动并再次触发 STARTED 补发布；
        // 这里先反注册旧版本实例，避免新旧并存
        unpublishBundle(symbolicName);
    }

    @Override
    public void onBundleUninstalled(String symbolicName) {
        unpublishBundle(symbolicName);
    }

    @Override
    public void onBundleStateChanged(String symbolicName, String oldState, String newState) {
        // 状态变化由 STARTED / STOPPED 细化回调处理
    }

    /**
     * 发布 bundle 中的 SPI 实现。
     *
     * @param symbolicName bundle 符号名
     */
    private synchronized void publishBundle(String symbolicName) {
        if (closed || !options.isEnabled() || publisher == null || !publisher.isActive()) {
            return;
        }
        if (symbolicName == null || symbolicName.isBlank() || symbolicName.startsWith("#")) {
            // 无符号名的裸 bundle 无法按符号名反注册，跳过
            return;
        }
        // 幂等：重复 STARTED 先清理旧注册
        List<Object> previous = publishedByBundle.remove(symbolicName);
        hostBridge.unregister(symbolicName);
        unpublishAll(previous);
        Bundle bundle = nativeBundle(symbolicName);
        if (bundle == null) {
            return;
        }
        ClassLoader classLoader = classLoaderOf(bundle);
        if (classLoader == null) {
            log.warn("[OSGi] bundle {} 无法获取 classloader，跳过 SPI 自动发布", symbolicName);
            return;
        }
        Set<Class<?>> implClasses = new LinkedHashSet<>();
        boolean indexed = readSpiIndex(symbolicName, bundle, classLoader, implClasses);
        if (!indexed) {
            scanSpiClasses(symbolicName, bundle, classLoader, implClasses);
        }
        if (implClasses.isEmpty()) {
            return;
        }
        List<Object> instances = new ArrayList<>();
        for (Class<?> impl : implClasses) {
            Object instance = instantiate(symbolicName, impl);
            if (instance == null) {
                continue;
            }
            boolean registered = false;
            for (Class<?> type : SpiPublishSupport.publishableInterfaces(impl)) {
                if (publishSafely(symbolicName, type, instance)) {
                    registered = true;
                    hostBridge.register(symbolicName, type, instance);
                }
            }
            if (registered) {
                instances.add(instance);
            } else {
                closeOwnedInstance(instance);
            }
        }
        if (!instances.isEmpty()) {
            publishedByBundle.put(symbolicName, instances);
            log.info("[OSGi] bundle {} 自动发布 {} 个 SPI 服务", symbolicName, instances.size());
        }
    }

    /**
     * 反注册 bundle 发布的全部 SPI 服务。
     *
     * @param symbolicName bundle 符号名
     */
    private synchronized void unpublishBundle(String symbolicName) {
        if (symbolicName == null || symbolicName.isBlank()) {
            return;
        }
        List<Object> instances = publishedByBundle.remove(symbolicName);
        hostBridge.unregister(symbolicName);
        unpublishAll(instances);
    }

    /**
     * 反注册实例列表（幂等）。
     *
     * @param instances 已发布实例
     */
    private void unpublishAll(List<Object> instances) {
        if (CollectionUtils.isEmpty(instances)) {
            return;
        }
        int removed = 0;
        for (Object instance : instances) {
            try {
                if (publisher != null && publisher.unpublish(instance)) {
                    removed++;
                }
            } catch (Exception | LinkageError ex) {
                log.warn("[OSGi] SPI 服务反注册失败: {}", ex.getMessage());
            }
            closeOwnedInstance(instance);
        }
        if (removed > 0) {
            log.info("[OSGi] 已反注册 {} 个 SPI 服务", removed);
        }
    }

    /** 只释放本发布器亲自构造的实例，不接管 Spring 或外部注册对象。 */
    private void closeOwnedInstance(Object instance) {
        if (instance instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception | LinkageError ex) {
                log.warn("[OSGi] SPI 资源释放失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 读取 bundle 内标准 SPI 索引。
     *
     * @param symbolicName bundle 符号名
     * @param bundle       原生 bundle
     * @param classLoader  bundle classloader
     * @param implClasses  收集到的实现类
     * @return 存在索引目录返回 {@code true}（即使个别实现类加载失败）
     */
    private boolean readSpiIndex(String symbolicName, Bundle bundle, ClassLoader classLoader,
                                 Set<Class<?>> implClasses) {
        Enumeration<URL> entries;
        try {
            entries = bundle.findEntries(SPI_INDEX_DIR, null, false);
        } catch (Exception ex) {
            log.warn("[OSGi] bundle {} 读取 SPI 索引失败: {}", symbolicName, ex.getMessage());
            return false;
        }
        if (entries == null || !entries.hasMoreElements()) {
            return false;
        }
        while (entries.hasMoreElements()) {
            URL url = entries.nextElement();
            String fileName = url.getPath();
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String implName = line.trim();
                    if (implName.isEmpty() || implName.startsWith("#")) {
                        continue;
                    }
                    loadSpiClass(symbolicName, implName, classLoader, implClasses);
                }
            } catch (Exception ex) {
                log.warn("[OSGi] bundle {} SPI 索引 {} 读取失败: {}", symbolicName, fileName, ex.getMessage());
            }
        }
        return true;
    }

    /**
     * 无索引时扫描 bundle 内标注 {@link Spi} 的可实例化类（带数量上限）。
     *
     * @param symbolicName bundle 符号名
     * @param bundle       原生 bundle
     * @param classLoader  bundle classloader
     * @param implClasses  收集到的实现类
     */
    private void scanSpiClasses(String symbolicName, Bundle bundle, ClassLoader classLoader,
                                Set<Class<?>> implClasses) {
        int max = Math.max(1, options.getMaxScanClasses());
        int visited = 0;
        Enumeration<URL> entries;
        try {
            entries = bundle.findEntries("/", "*.class", true);
        } catch (Exception ex) {
            log.warn("[OSGi] bundle {} 扫描 class 失败: {}", symbolicName, ex.getMessage());
            return;
        }
        if (entries == null) {
            return;
        }
        while (entries.hasMoreElements() && visited < max) {
            URL url = entries.nextElement();
            visited++;
            String className = classNameOf(url);
            if (className == null || className.indexOf('$') >= 0) {
                continue;
            }
            loadSpiClass(symbolicName, className, classLoader, implClasses);
        }
        if (visited >= max) {
            log.warn("[OSGi] bundle {} class 扫描达到上限 {}，可能遗漏 @Spi 实现", symbolicName, max);
        }
    }

    /**
     * 加载并收集标注 {@link Spi} 的实现类（不初始化）。
     *
     * @param symbolicName bundle 符号名
     * @param className    实现类全限定名
     * @param classLoader  bundle classloader
     * @param implClasses  收集结果
     */
    private void loadSpiClass(String symbolicName, String className, ClassLoader classLoader,
                              Set<Class<?>> implClasses) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            if (clazz.isAnnotationPresent(Spi.class) && !clazz.isInterface()
                    && !Modifier.isAbstract(clazz.getModifiers())) {
                implClasses.add(clazz);
            }
        } catch (Throwable ex) {
            // 依赖缺失等原因加载失败，跳过该类，不影响其余 SPI 发布
            log.debug("[OSGi] bundle {} SPI 类 {} 加载失败: {}", symbolicName, className, ex.getMessage());
        }
    }

    /**
     * 由 bundle 资源 URL 解析 class 全限定名。
     *
     * @param url class 资源 URL
     * @return 全限定名；无法解析返回 {@code null}
     */
    private String classNameOf(URL url) {
        String path = url.getPath();
        if (path == null || !path.endsWith(".class")) {
            return null;
        }
        path = path.substring(0, path.length() - ".class".length());
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.replace('/', '.');
    }

    /**
     * 实例化 SPI 实现（无参构造）。
     *
     * @param symbolicName bundle 符号名
     * @param clazz        实现类
     * @return 实例；失败返回 {@code null}
     */
    private Object instantiate(String symbolicName, Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable ex) {
            log.warn("[OSGi] bundle {} SPI 实现 {} 实例化失败: {}", symbolicName, clazz.getName(), ex.getMessage());
            return null;
        }
    }

    /**
     * 按捕获类型安全发布。
     *
     * @param symbolicName bundle 符号名
     * @param type         服务接口类型
     * @param instance     服务实例
     * @param <T>          服务类型
     * @return 发布成功返回 {@code true}
     */
    private <T> boolean publishSafely(String symbolicName, Class<T> type, Object instance) {
        try {
            return publisher.publish(type, type.cast(instance));
        } catch (Exception | LinkageError ex) {
            log.warn("[OSGi] bundle {} SPI 服务发布失败: {} - {}", symbolicName, type.getName(), ex.getMessage());
            return false;
        }
    }

    /**
     * 按符号名取框架内原生 bundle。
     *
     * <p>同一符号名存在多个版本并存时（先装旧版再装新版、旧版未卸载），
     * 取「活动态优先、版本最高、bundleId 最大」的 bundle，保证新版本胜出；
     * 若直接按符号名取第一个，会把旧版本实例重新发布，导致新版本不生效。</p>
     *
     * @param symbolicName bundle 符号名
     * @return 原生 bundle；不存在返回 {@code null}
     */
    private Bundle nativeBundle(String symbolicName) {
        BundleContext context = launcher.getFrameworkBundleContext();
        if (context == null) {
            return null;
        }
        OsgiBundle best = null;
        for (OsgiBundle candidate : launcher.getBundles()) {
            if (candidate == null || !symbolicName.equals(candidate.getSymbolicName())) {
                continue;
            }
            if (best == null) {
                best = candidate;
                continue;
            }
            if (candidate.isActive() != best.isActive()) {
                // 活动态优先：非活动 bundle 的 classloader 不可靠
                if (candidate.isActive()) {
                    best = candidate;
                }
                continue;
            }
            int byVersion = versionOf(candidate).compareTo(versionOf(best));
            if (byVersion > 0 || (byVersion == 0 && candidate.getBundleId() > best.getBundleId())) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        return context.getBundle(best.getBundleId());
    }

    /**
     * 解析 bundle 版本号（不可解析时按 0.0.0 处理）。
     *
     * @param bundle bundle 包装
     * @return OSGi 版本号
     */
    private static org.osgi.framework.Version versionOf(OsgiBundle bundle) {
        try {
            return org.osgi.framework.Version.parseVersion(bundle.getVersion());
        } catch (Exception ex) {
            return org.osgi.framework.Version.emptyVersion;
        }
    }

    /**
     * 取 bundle 自身的 classloader。
     *
     * @param bundle 原生 bundle
     * @return classloader；不可用返回 {@code null}
     */
    private ClassLoader classLoaderOf(Bundle bundle) {
        try {
            BundleWiring wiring = bundle.adapt(BundleWiring.class);
            return wiring == null ? null : wiring.getClassLoader();
        } catch (Exception ex) {
            return null;
        }
    }
}
