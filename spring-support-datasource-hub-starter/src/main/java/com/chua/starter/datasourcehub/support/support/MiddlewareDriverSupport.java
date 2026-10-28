package com.chua.starter.datasourcehub.support.support;

import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用上传的 jar 提供中间件客户端实现。
 *
 * <p><b>为什么要有这一层</b>：中间件客户端（Kafka、MongoDB、ZooKeeper …）的体积与许可
 * 各不相同，全部打进产物等于强制每个部署都背上几十 MB 用不上的依赖；
 * 而让用户往 classpath 里塞 jar 又不可控。于是沿用 JDBC 驱动的做法：
 * 上传 → 落盘 → 按 jar 建独立类加载器 → 运行期反射实例化。</p>
 *
 * <p><b>与 {@link JdbcDriverSupport} 的关键差异</b>：JDBC 驱动的父加载器必须是
 * 「平台类加载器」，让 {@code java.sql.Driver} 与驱动实现分属不同加载器、互不污染；
 * 这里的父加载器反过来必须是<b>应用类加载器</b>——因为 {@link MiddlewareClientProvider}
 * 这个接口来自应用自己的 classpath，如果父加载器换掉，jar 里的实现就会实现一个
 * 「另一个加载器里的同名接口」，宿主的 {@code instanceof} 与 SPI 转换全部失败，
 * 报的还是「不是 MiddlewareClientProvider 的实现」这种看不懂的错。</p>
 *
 * <p><b>单 jar 约束</b>：这里只把上传的那一个 jar 加进加载器，不解析 {@code pom}、
 * 不下载传递依赖。所以上传的必须是「自带全部依赖的单 jar」（shade/fat jar）；
 * 缺依赖时会在实例化阶段抛 {@code NoClassDefFoundError}，由上传接口当场回滚并提示。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
public final class MiddlewareDriverSupport {

    /**
     * 已加载的中间件实现缓存：{@code sha256#实现类名} → 条目。
     *
     * <p>按内容指纹做键：同名 jar 换了内容会自然重建，而重复上传同一份不会反复建加载器。</p>
     */
    private static final Map<String, ProviderEntry> CACHE = new ConcurrentHashMap<>();

    /**
     * 已加载实现按「中间件类型名」索引，供 {@code MiddlewareClientRegistry} 合并。
     *
     * <p>用 {@link LinkedHashMap} 保序，便于「已装配类型」列表稳定可读。</p>
     */
    private static final Map<String, MiddlewareClientProvider> BY_NAME =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private MiddlewareDriverSupport() {
    }

    /**
     * 缓存项：类加载器 + 实现实例。
     *
     * @param loader   该 jar 专属的类加载器
     * @param provider 实现实例
     * @param jarName  落盘文件名，日志与排障用
     */
    private record ProviderEntry(URLClassLoader loader, MiddlewareClientProvider provider, String jarName) {
    }

    /**
     * 计算 jar 的 sha256（缓存键 + 台账指纹）。
     *
     * @param jar jar 文件
     * @return 小写十六进制 sha256
     * @throws IOException 读文件失败
     */
    public static String sha256(File jar) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(jar.toPath())));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 取（或加载）指定 jar 里的中间件实现。
     *
     * @param jarFile    jar 文件
     * @param providerClassName 实现类全名
     * @return 实现实例
     * @throws Exception jar 不存在、类不在 jar 里、类无法实例化、依赖缺失
     */
    public static MiddlewareClientProvider providerOf(File jarFile, String providerClassName) throws Exception {
        if (jarFile == null || !jarFile.isFile()) {
            throw new IllegalArgumentException("驱动 jar 不存在: " + jarFile);
        }
        if (providerClassName == null || providerClassName.isBlank()) {
            throw new IllegalArgumentException("实现类名不能为空");
        }
        String className = providerClassName.trim();
        String key = sha256(jarFile) + "#" + className;
        ProviderEntry cached = CACHE.get(key);
        if (cached != null) {
            return cached.provider();
        }
        URL url;
        try {
            url = jarFile.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IOException("驱动 jar 路径无法转成 URL: " + jarFile, e);
        }
        // 父加载器必须是应用类加载器：SPI 接口要从这里共享，理由见类注释
        URLClassLoader loader = new URLClassLoader(
                "middleware-driver:" + jarFile.getName(), new URL[]{url},
                MiddlewareDriverSupport.class.getClassLoader());
        try {
            Class<?> clazz = Class.forName(className, true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof MiddlewareClientProvider provider)) {
                throw new IllegalStateException(className
                        + " 不是 MiddlewareClientProvider 的实现（实际类型 " + instance.getClass().getName() + "）");
            }
            String type = provider.name();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException(className + " 的 name() 返回空，无法确定它对应哪种中间件");
            }
            ProviderEntry entry = new ProviderEntry(loader, provider, jarFile.getName());
            CACHE.put(key, entry);
            BY_NAME.put(type.toLowerCase(), provider);
            log.info("[datasource-hub] 已从上传的 jar 加载中间件客户端: {} <- {} (type={})",
                    className, jarFile.getName(), type);
            return provider;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            closeQuietly(loader);
            throw new IllegalStateException(
                    "从 jar 加载中间件客户端失败: " + className + "（jar=" + jarFile.getName() + "）: " + e, e);
        }
    }

    /**
     * 已从上传 jar 装配的实现（类型名 → 实现），供注册表合并。
     *
     * @return 只读视图
     */
    public static Map<String, MiddlewareClientProvider> uploaded() {
        synchronized (BY_NAME) {
            return Map.copyOf(BY_NAME);
        }
    }

    /**
     * 丢弃某个 jar 的缓存（删除/替换 jar 后调用），并关闭其类加载器。
     *
     * @param jar jar 文件
     */
    public static void evict(File jar) {
        if (jar == null) {
            return;
        }
        String prefix;
        try {
            prefix = sha256(jar) + "#";
        } catch (IOException e) {
            log.warn("[datasource-hub] 计算 jar 指纹失败，无法清中间件缓存: {}", e.getMessage());
            return;
        }
        CACHE.keySet().stream().filter(key -> key.startsWith(prefix)).toList().forEach(key -> {
            ProviderEntry entry = CACHE.remove(key);
            if (entry != null) {
                BY_NAME.remove(entry.provider().name().toLowerCase());
                closeQuietly(entry.loader());
            }
        });
    }

    /**
     * 清空全部缓存（测试与热更新用）。
     */
    public static void clearCache() {
        CACHE.values().forEach(entry -> closeQuietly(entry.loader()));
        CACHE.clear();
        BY_NAME.clear();
    }

    /**
     * 当前已装配的实现数量（便于验收观察）。
     *
     * @return 条数
     */
    public static int cacheSize() {
        return CACHE.size();
    }

    /**
     * 关闭类加载器（释放 jar 文件句柄）。
     *
     * @param loader 类加载器，允许空
     */
    private static void closeQuietly(URLClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (IOException e) {
            log.debug("[datasource-hub] 关闭中间件类加载器失败: {}", e.getMessage());
        }
    }
}
