package com.chua.starter.job.support.glue.compile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 运行时类路径索引：把宿主类加载器能看到的 {@code .class} 摊平成
 * “二进制类名 → {@link ClassPathEntry}”，供 {@code javac} 在编译胶水片段时解析类型。
 *
 * <p>为什么不能直接用 {@code -classpath $(java.class.path)}：Spring Boot 的 fat jar 启动时
 * 该属性只有一个值——自己那个 {@code app.jar}，{@code BOOT-INF/classes} 与
 * {@code BOOT-INF/lib/*.jar} 都在 jar 内部，{@code javac} 一个也读不到；而胶水片段
 * 恰恰要 import 业务类。索引走的是 {@link URLClassLoader} 的 URL，fat jar 下每条是
 * {@code jar:file:/app.jar!/BOOT-INF/lib/xxx.jar!/}，用 {@link JarURLConnection} 能取到
 * 对应的 {@link JarFile}，嵌套 jar 因此也能读到。</p>
 *
 * <p>索引按类加载器身份缓存：类加载器换了（热部署、测试）会重建一次。JDK 自带类不在索引里
 * ——引导类加载器不是 {@link URLClassLoader}，{@code javac} 自己会用 {@code jrt-fs} 解析。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
final class RuntimeClassPath {

    /**
     * 索引：二进制类名 → 条目
     */
    private static volatile Map<String, ClassPathEntry> index;

    /**
     * 构建索引时对应的类加载器
     */
    private static volatile ClassLoader indexedLoader;

    /**
     * 取运行时类路径索引。
     *
     * @return 二进制类名到条目的映射，只读
     */
    static Map<String, ClassPathEntry> index() {
        var loader = Thread.currentThread().getContextClassLoader();
        var cached = index;
        if (cached != null && indexedLoader == loader) {
            return cached;
        }
        synchronized (RuntimeClassPath.class) {
            cached = index;
            if (cached != null && indexedLoader == loader) {
                return cached;
            }
            cached = build(loader);
            index = cached;
            indexedLoader = loader;
            return cached;
        }
    }

    /**
     * 遍历类加载器链上的全部 {@link URLClassLoader} URL 建索引；
     * 一条都没收集到时退回 {@code java.class.path}。
     *
     * @param loader 起始类加载器，允许为 null
     * @return 索引
     */
    private static Map<String, ClassPathEntry> build(ClassLoader loader) {
        var entries = new HashMap<String, ClassPathEntry>(4096);
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            if (!(current instanceof URLClassLoader urlLoader)) {
                continue;
            }
            for (URL url : urlLoader.getURLs()) {
                collect(url, entries);
            }
        }
        if (entries.isEmpty()) {
            collectFromJavaClasspath(entries);
        }
        return Map.copyOf(entries);
    }

    /**
     * 按 URL 协议分派收集。
     *
     * @param url     类路径条目 URL
     * @param entries 索引收集器
     */
    private static void collect(URL url, Map<String, ClassPathEntry> entries) {
        var protocol = url.getProtocol();
        if ("file".equalsIgnoreCase(protocol)) {
            try {
                var path = Path.of(url.toURI());
                if (Files.isDirectory(path)) {
                    collectDirectory(path, entries);
                } else if (Files.isRegularFile(path)) {
                    collectJar(path, entries);
                }
            } catch (IOException | URISyntaxException | RuntimeException ex) {
                // 单条 URL 不可读不能拖垮整个编译，跳过后 javac 会报“找不到符号”，错误信息更准
            }
            return;
        }
        if ("jar".equalsIgnoreCase(protocol)) {
            collectNestedJar(url, entries);
        }
    }

    /**
     * 收集展开目录下的 class 文件。
     *
     * @param root    根目录
     * @param entries 索引收集器
     * @throws IOException 遍历失败
     */
    private static void collectDirectory(Path root, Map<String, ClassPathEntry> entries) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .forEach(path -> {
                        var relative = root.relativize(path).toString()
                                .replace(File.separatorChar, '/');
                        var binaryName = relative.substring(0, relative.length() - ".class".length());
                        put(entries, binaryName, path);
                    });
        }
    }

    /**
     * 收集普通 jar 内的 class 文件。
     *
     * @param jarPath jar 路径
     * @param entries 索引收集器
     */
    private static void collectJar(Path jarPath, Map<String, ClassPathEntry> entries) {
        try (var jar = new JarFile(jarPath.toFile())) {
            collectJarFile(jar, entries);
        } catch (IOException ex) {
            // 同上，坏 jar 跳过即可
        }
    }

    /**
     * 收集嵌套 jar（Spring Boot fat jar 的 {@code jar:file:/app.jar!/BOOT-INF/lib/x.jar!/}）内的 class。
     *
     * <p>不关闭连接返回的 {@link JarFile}：{@code JarURLConnection} 默认带缓存，
     * 关掉会让类加载器后续读取自己 URL 时拿到已关闭句柄。</p>
     *
     * @param url     嵌套 jar URL
     * @param entries 索引收集器
     */
    private static void collectNestedJar(URL url, Map<String, ClassPathEntry> entries) {
        try {
            if (!(url.openConnection() instanceof JarURLConnection connection)) {
                return;
            }
            collectJarFile(connection.getJarFile(), entries);
        } catch (IOException | RuntimeException ex) {
            // 同上
        }
    }

    /**
     * 收集一个已打开 jar 里的全部 class 条目。
     *
     * @param jar     已打开的 jar
     * @param entries 索引收集器
     */
    private static void collectJarFile(JarFile jar, Map<String, ClassPathEntry> entries) {
        Enumeration<JarEntry> jarEntries = jar.entries();
        while (jarEntries.hasMoreElements()) {
            var entry = jarEntries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                continue;
            }
            var binaryName = entry.getName()
                    .substring(0, entry.getName().length() - ".class".length())
                    .replace('/', '.');
            put(entries, binaryName, entry.getName(), jar);
        }
    }

    /**
     * 索引里登记一个目录下的 class。
     *
     * @param entries 索引收集器
     * @param name    二进制类名（点分）
     * @param path    class 文件路径
     */
    private static void put(Map<String, ClassPathEntry> entries, String name, Path path) {
        entries.computeIfAbsent(name, key -> new ClassPathEntry(key, uriOf(key),
                () -> Files.newInputStream(path)));
    }

    /**
     * 索引里登记一个 jar 内的 class。
     *
     * @param entries   索引收集器
     * @param name      二进制类名（点分）
     * @param entryName jar 内条目名
     * @param jar       jar 文件
     */
    private static void put(Map<String, ClassPathEntry> entries, String name, String entryName, JarFile jar) {
        entries.computeIfAbsent(name, key -> new ClassPathEntry(key, uriOf(key), () -> {
            var jarEntry = jar.getEntry(entryName);
            if (jarEntry == null) {
                throw new IOException("jar 条目不存在: " + entryName);
            }
            return jar.getInputStream(jarEntry);
        }));
    }

    /**
     * 收集 {@code java.class.path} 上的条目（类加载器链没给出任何 URL 时的兜底，
     * 典型是极简启动器或自研 classloader）。
     *
     * @param entries 索引收集器
     */
    private static void collectFromJavaClasspath(Map<String, ClassPathEntry> entries) {
        var raw = System.getProperty("java.class.path");
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String item : raw.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (item.isBlank()) {
                continue;
            }
            var path = Path.of(item);
            if (Files.isDirectory(path)) {
                try {
                    collectDirectory(path, entries);
                } catch (IOException ex) {
                    // 跳过
                }
            } else if (Files.isRegularFile(path)) {
                collectJar(path, entries);
            }
        }
    }

    /**
     * 生成类路径内唯一的合成 URI。
     *
     * @param binaryName 二进制类名（点分）
     * @return 合成 URI
     */
    private static URI uriOf(String binaryName) {
        return URI.create("jobjl-glue-class:///" + binaryName.replace('.', '/') + ".class");
    }

    /**
     * 按包名分组索引，供 {@code javac} 做 O(子包数) 的包解析而非全量扫描。
     *
     * @param entries 索引
     * @return 包名（点分，根包为空串）到条目列表的映射
     */
    static Map<String, List<ClassPathEntry>> groupByPackage(Map<String, ClassPathEntry> entries) {
        var grouped = new LinkedHashMap<String, List<ClassPathEntry>>(entries.size());
        for (var entry : entries.values()) {
            var name = entry.binaryName();
            var lastDot = name.lastIndexOf('.');
            var packageName = lastDot < 0 ? "" : name.substring(0, lastDot);
            grouped.computeIfAbsent(packageName, key -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    /**
     * 禁止实例化 运行时类路径索引
     */
    private RuntimeClassPath() {
    }
}
