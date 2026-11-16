package com.chua.starter.ai.support.manage.limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * 限制机制注册表：把 {@link ProviderLimitProvider} 的各种接入方式收敛成一条有序链。
 *
 * <p>装载优先级（同一 {@code kind} 只保留一个）：</p>
 * <ol>
 *   <li>Spring 容器里的 Bean（{@code ProviderLimitAutoConfiguration} 注入后调用 {@link #install}）；</li>
 *   <li>{@code META-INF/services} 登记的实现（{@link ServiceLoader}）；</li>
 *   <li>内置兜底（手动 / 官方文档 / 响应头），仅在容器和 SPI 文件都没有时使用。</li>
 * </ol>
 *
 * <p>结果按 {@link ProviderLimitProvider#order()} 升序排列，顺序即「谁先命中谁生效」。
 * 注册表在首次使用时构建一次；{@link #install} 可覆盖（容器启动、测试重置）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ProviderLimitSpi {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderLimitSpi.class);

    private static final Object LOCK = new Object();

    /**
     * 已安装的机制链；null 表示尚未解析
     */
    private static volatile List<ProviderLimitProvider> installed;

    private ProviderLimitSpi() {
    }

    /**
     * 当前生效的机制链（首次调用时解析并缓存）
     *
     * @return 有序、不可变的机制列表
     */
    public static List<ProviderLimitProvider> providers() {
        List<ProviderLimitProvider> current = installed;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (installed == null) {
                installed = resolve(List.of());
            }
            return installed;
        }
    }

    /**
     * 安装 Spring 容器里的机制实现；与 ServiceLoader 结果合并后覆盖当前链。
     *
     * @param springProviders 容器注入的实现（可为空）
     */
    public static void install(List<ProviderLimitProvider> springProviders) {
        synchronized (LOCK) {
            installed = resolve(springProviders == null ? List.of() : springProviders);
            LOG.info("[ProviderLimit] 机制链已装载：{}", kinds());
        }
    }

    /**
     * 清空缓存，下次 {@link #providers()} 重新解析（测试用）。
     */
    public static void reset() {
        synchronized (LOCK) {
            installed = null;
        }
    }

    /**
     * 机制标识清单，按执行顺序。
     *
     * @return 机制标识
     */
    public static List<String> kinds() {
        List<ProviderLimitProvider> current = providers();
        List<String> kinds = new ArrayList<>(current.size());
        for (ProviderLimitProvider provider : current) {
            kinds.add(provider.kind());
        }
        return kinds;
    }

    /**
     * 合并 Spring Bean、ServiceLoader 与内置兜底，按 kind 去重、按 order 排序。
     *
     * @param springProviders 容器注入的实现
     * @return 合并结果
     */
    private static List<ProviderLimitProvider> resolve(List<ProviderLimitProvider> springProviders) {
        Map<String, ProviderLimitProvider> byKind = new LinkedHashMap<>(8);
        for (ProviderLimitProvider provider : springProviders) {
            if (provider != null && provider.kind() != null) {
                byKind.put(provider.kind(), provider);
            }
        }
        for (ProviderLimitProvider provider : loadServices()) {
            if (provider.kind() != null) {
                byKind.putIfAbsent(provider.kind(), provider);
            }
        }
        if (byKind.isEmpty()) {
            // 没有容器也没有 SPI 文件（例如裁剪过的运行镜像）时退回内置实现
            for (ProviderLimitProvider provider : builtins()) {
                byKind.put(provider.kind(), provider);
            }
        }
        List<ProviderLimitProvider> list = new ArrayList<>(byKind.values());
        list.sort(Comparator.comparingInt(ProviderLimitProvider::order)
                .thenComparing(ProviderLimitProvider::kind));
        return List.copyOf(list);
    }

    /**
     * 从 {@code META-INF/services} 加载扩展实现；单个实现加载失败不影响其它机制。
     *
     * @return 已加载的实现
     */
    private static List<ProviderLimitProvider> loadServices() {
        List<ProviderLimitProvider> list = new ArrayList<>(4);
        try {
            for (ProviderLimitProvider provider : ServiceLoader.load(ProviderLimitProvider.class)) {
                list.add(provider);
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            LOG.warn("[ProviderLimit] ServiceLoader 装载失败，仅使用已加载的机制：{}", e.toString());
        }
        return list;
    }

    /**
     * 内置兜底实现（与 ServiceLoader 文件中的三条一致）。
     *
     * @return 内置机制
     */
    private static List<ProviderLimitProvider> builtins() {
        return List.of(
                new ManualProviderLimitProvider(),
                new DocProviderLimitProvider(),
                new HeaderProviderLimitProvider()
        );
    }
}
