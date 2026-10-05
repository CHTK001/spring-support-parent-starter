package com.chua.starter.datasourcehub.support.support;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用上传的 jar 驱动 JDBC 数据源。
 *
 * <p><b>为什么不能直接用 {@link DriverManager}</b>：它只认「已经被
 * {@code Class.forName} 加载过、且注册进静态 DriverManager列表」的驱动，
 * 而那些注册发生在**应用自己的类加载器**上。用户上传的驱动 jar 在应用 classpath 之外，
 * {@code Class.forName} 根本找不到类，于是报
 * {@code ClassNotFoundException: com.mysql.cj.jdbc.Driver} —— 用户会以为是驱动版本不对，
 * 真实原因是「这个 jar 根本没被加载」。</p>
 *
 * <p>所以这里为每个 jar 建一个独立的 {@link URLClassLoader}，用它的
 * {@code loadClass} 拿到驱动类、反射实例化成 {@link Driver}，
 * 再直接调 {@link Driver#connect}，绕开 DriverManager 的注册表。</p>
 *
 * <p><b>类加载器隔离的坑</b>：如果把 {@code URLClassLoader} 的父加载器设为应用类加载器
 * （{@code new URLClassLoader(urls, this.getClass().getClassLoader())}），
 * jar 里那份 {@code java.sql.Driver} 接口会由父加载器先命中，
 * 于是驱动对象其实没从 jar 里来，仍然报缺类/版本冲突。所以这里用
 * 「平台类加载器」做父加载器：JDK 核心类（java.sql.*）由平台加载器提供，
 * 驱动实现及其依赖（org.mariadb.*、org.h2.*、org.sqlite.*）从 jar 里加载，
 * 互不污染，也就不会和别的数据源用的同名驱动互相覆盖。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public final class JdbcDriverSupport {

    /**
     * 类加载器与驱动实例的缓存：jar 的 sha256 -> 加载器。
     *
     * <p>按 jar 内容而不是路径做键：同名文件被换掉（升级驱动）后会自然重建，
     * 而路径不变时不会反复建加载器。</p>
     */
    private static final Map<String, DriverEntry> CACHE = new ConcurrentHashMap<>();

    private JdbcDriverSupport() {
    }

    /**
     * 缓存项：类加载器 + 驱动实例。
     *
     * @param loader     该 jar 专属的类加载器
     * @param driver     驱动实例
     * @param driverClass 驱动类全名
     */
    private record DriverEntry(URLClassLoader loader, Driver driver, String driverClass) {
    }

    /**
     * 计算 jar 的 sha256（用作缓存键 + 台账里的指纹）。
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
     * 取（或加载）指定 jar 里的驱动实例。
     *
     * @param jarFile          jar 文件
     * @param driverClassName  驱动类全名，如 {@code org.h2.Driver}
     * @return 驱动实例
     * @throws Exception jar 不存在、类不在 jar 里、类无法实例化
     */
    public static Driver driverOf(File jarFile, String driverClassName) throws Exception {
        if (jarFile == null || !jarFile.isFile()) {
            throw new IllegalArgumentException("驱动 jar 不存在: " + jarFile);
        }
        if (driverClassName == null || driverClassName.isBlank()) {
            throw new IllegalArgumentException("驱动类名不能为空");
        }
        String key = sha256(jarFile) + "#" + driverClassName.trim();
        DriverEntry cached = CACHE.get(key);
        if (cached != null) {
            return cached.driver();
        }
        URL url;
        try {
            url = jarFile.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IOException("驱动 jar 路径无法转成 URL: " + jarFile, e);
        }
        // 父加载器用「平台类加载器」而不是应用类加载器 —— 理由见类注释
        URLClassLoader loader =
                new URLClassLoader("jdbc-driver:" + jarFile.getName(), new URL[]{url}, ClassLoader.getPlatformClassLoader());
        try {
            Class<?> clazz = Class.forName(driverClassName.trim(), true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Driver driver)) {
                throw new IllegalStateException(
                        driverClassName + " 不是 java.sql.Driver 的实现（实际类型 " + instance.getClass().getName() + "）");
            }
            DriverEntry entry = new DriverEntry(loader, driver, driverClassName.trim());
            CACHE.put(key, entry);
            log.info("[datasource-hub] 已从上传的 jar 加载驱动: {} <- {}",
                    driverClassName.trim(), jarFile.getName());
            return driver;
        } catch (ReflectiveOperationException | RuntimeException e) {
            closeQuietly(loader);
            throw new IllegalStateException(
                    "从 jar 加载驱动类失败: " + driverClassName + "（jar=" + jarFile.getName() + "）: " + e, e);
        }
    }

    /**
     * 用上传 jar 里的驱动建连。
     *
     * <p>驱动 {@code connect} 返回 {@code null} 表示「这个 URL 不归我管」，
     * 这时不能静默当成失败 —— 用户会看到一句没有信息量的
     * 「驱动拒绝了该连接串」。这里改成明确的提示，指出去查连接串。</p>
     *
     * @param jarFile         jar 文件
     * @param driverClassName 驱动类全名
     * @param url             JDBC 连接串
     * @param props           连接属性
     * @return 连接
     * @throws Exception 加载或建连失败
     */
    public static Connection connect(File jarFile, String driverClassName, String url, Properties props)
            throws Exception {
        Driver driver = driverOf(jarFile, driverClassName);
        Connection connection = driver.connect(url, props);
        if (connection == null) {
            throw new IllegalStateException("驱动 " + driverClassName + " 不接受该连接串: " + url
                    + "（驱动只处理自己认识的子协议，检查 URL 前缀与驱动是否匹配）");
        }
        return connection;
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
            log.warn("[datasource-hub] 计算 jar 指纹失败，无法清缓存: {}", e.getMessage());
            return;
        }
        CACHE.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList()
                .forEach(key -> {
                    DriverEntry entry = CACHE.remove(key);
                    if (entry != null) {
                        closeQuietly(entry.loader());
                    }
                });
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
            log.debug("[datasource-hub] 关闭驱动类加载器失败: {}", e.getMessage());
        }
    }

    /**
     * 当前缓存的驱动数量（便于验收观察）。
     *
     * @return 条数
     */
    public static int cacheSize() {
        return CACHE.size();
    }

    /**
     * 清空全部缓存（测试与热更新用）。
     */
    public static void clearCache() {
        CACHE.values().forEach(entry -> closeQuietly(entry.loader()));
        CACHE.clear();
    }
}