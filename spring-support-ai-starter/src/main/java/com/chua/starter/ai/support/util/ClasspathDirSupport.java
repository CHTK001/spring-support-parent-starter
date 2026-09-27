package com.chua.starter.ai.support.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * classpath 目录资源读取支持。
 *
 * <p>用于修补 Spring Boot 可执行 jar（fat jar）下的扫描盲区：{@code PathMatchingResourcePatternResolver}
 * 解析 {@code classpath*:xxx/**} 时，会先调用 {@code ClassLoader#getResources("xxx/")} 枚举目录项，
 * 再在每个目录下递归匹配。而 Spring Boot 的嵌套 jar 类加载器把 {@code BOOT-INF/classes} 当作
 * 一个嵌套 jar 根，<b>不返回其下的目录项</b>，于是根枚举为空，整棵树匹配不到任何文件，
 * 最终表现为「内置资源同步数 = 0」。</p>
 *
 * <p>本类按三级策略读取，任一级命中即返回：</p>
 * <ol>
 *   <li>常规 {@code classpath*:} 扫描：exploded 目录、IDE、依赖 jar 场景走这条路径；</li>
 *   <li>按本类 CodeSource 定位外层 jar 后遍历 zip 条目：兼容 Boot 新旧两种嵌套 URL 形态；</li>
 *   <li>遍历 {@code java.class.path} 上的每个 jar：完全不依赖 URL 形态解析，作为最终兜底。</li>
 * </ol>
 * <p>第 2、3 级都会同时匹配 {@code BOOT-INF/classes/xxx/} 与裸 {@code xxx/} 两种布局，
 * 因此既能读可执行 jar 里的应用类资源，也能读依赖 jar 里的资源。全部落空时返回空 Map，
 * 调用方按「无内置资源」处理，不抛异常打断启动。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public final class ClasspathDirSupport {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(ClasspathDirSupport.class);

    /**
     * 单个资源允许的最大字节数，避免误读超大条目把内存打满
     */
    private static final int MAX_ENTRY_BYTES = 32 * 1024 * 1024;

    /**
     * 工具类禁止实例化
     */
    private ClasspathDirSupport() {
    }

    /**
     * 读取 classpath 目录下匹配条件的全部文件。
     *
     * @param classpathDir classpath 目录，形如 {@code skills-builtin}（不带首尾斜杠）
     * @param nameFilter   文件名过滤器，入参为「相对目录的路径」，返回 {@code true} 才保留
     * @return 相对路径到文件内容的映射，保持遍历顺序；无资源时返回空 Map
     */
    public static Map<String, byte[]> readDirectory(String classpathDir, Predicate<String> nameFilter) {
        Map<String, byte[]> fromResolver = readByResolver(classpathDir, nameFilter);
        log.info("[ClasspathDir] {} 第1级 classpath* 扫描 count={}", classpathDir, fromResolver.size());
        if (!fromResolver.isEmpty()) {
            return fromResolver;
        }
        JarLocation location = JarLocation.resolve();
        if (location != null) {
            Map<String, byte[]> fromCodeSource = readByJarEntries(
                    classpathDir, nameFilter, location.jarPath(), location.nestedRoot());
            log.info("[ClasspathDir] {} 第2级 CodeSource jar 兜底 count={} jar={} nestedRoot={}",
                    classpathDir, fromCodeSource.size(), location.jarPath(), location.nestedRoot());
            if (!fromCodeSource.isEmpty()) {
                return fromCodeSource;
            }
        } else {
            log.info("[ClasspathDir] {} 第2级跳过：CodeSource 非 jar 形态", classpathDir);
        }
        Map<String, byte[]> fromClasspath = readByClasspathJars(classpathDir, nameFilter);
        log.info("[ClasspathDir] {} 第3级 java.class.path 兜底 count={}", classpathDir, fromClasspath.size());
        if (!fromClasspath.isEmpty()) {
            return fromClasspath;
        }
        log.warn("[ClasspathDir] {} 三级策略均未取到资源，内置内容将缺失", classpathDir);
        return Map.of();
    }

    /**
     * 读取 classpath 目录下的全部文件。
     *
     * @param classpathDir classpath 目录，形如 {@code characters-builtin}
     * @return 相对路径到文件内容的映射
     */
    public static Map<String, byte[]> readDirectory(String classpathDir) {
        return readDirectory(classpathDir, name -> true);
    }

    /**
     * 常规 {@code classpath*:} 扫描，exploded 目录 / IDE / 依赖 jar 走这条路径。
     *
     * @param classpathDir classpath 目录
     * @param nameFilter   文件名过滤器
     * @return 相对路径到文件内容的映射
     */
    private static Map<String, byte[]> readByResolver(String classpathDir, Predicate<String> nameFilter) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        String prefix = classpathDir + "/";
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:" + prefix + "**");
            for (Resource resource : resources) {
                String relative = relativePath(resource, prefix);
                if (relative == null || !nameFilter.test(relative)) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    result.put(relative, readAll(in));
                } catch (IOException e) {
                    // 单个资源读失败不影响其余资源
                    continue;
                }
            }
        } catch (IOException e) {
            return Map.of();
        }
        return result;
    }

    /**
     * 按 CodeSource 定位到的外层 jar 遍历条目。
     *
     * @param classpathDir classpath 目录
     * @param nameFilter   文件名过滤器
     * @param jarPath      外层 jar 路径
     * @param nestedRoot   jar 内嵌套根前缀，可为空串
     * @return 相对路径到文件内容的映射
     */
    private static Map<String, byte[]> readByJarEntries(String classpathDir, Predicate<String> nameFilter,
                                                        String jarPath, String nestedRoot) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        File jarFile = new File(jarPath);
        if (!jarFile.isFile()) {
            return result;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            collect(jar, nestedRoot + classpathDir + "/", nameFilter, result);
        } catch (IOException e) {
            return Map.of();
        }
        return result;
    }

    /**
     * 遍历 {@code java.class.path} 上的每个 jar，不依赖任何 URL 形态解析。
     *
     * @param classpathDir classpath 目录
     * @param nameFilter   文件名过滤器
     * @return 相对路径到文件内容的映射
     */
    private static Map<String, byte[]> readByClasspathJars(String classpathDir, Predicate<String> nameFilter) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        String classpath = System.getProperty("java.class.path", "");
        for (String element : classpath.split(File.pathSeparator)) {
            if (element.isBlank()) {
                continue;
            }
            File file = new File(element);
            if (!file.isFile() || !element.toLowerCase().endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(file)) {
                // 应用类在 BOOT-INF/classes 下，依赖类资源在 jar 根下，两种布局都覆盖
                collect(jar, "BOOT-INF/classes/" + classpathDir + "/", nameFilter, result);
                collect(jar, classpathDir + "/", nameFilter, result);
            } catch (IOException e) {
                continue;
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return result;
    }

    /**
     * 从一个已打开的 jar 里收集指定前缀下的文件。
     *
     * @param jar         已打开的 jar
     * @param prefix      条目前缀（含尾斜杠）
     * @param nameFilter  文件名过滤器
     * @param result      收集结果
     */
    private static void collect(JarFile jar, String prefix, Predicate<String> nameFilter, Map<String, byte[]> result) {
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix) || name.endsWith("/")) {
                continue;
            }
            String relative = name.substring(prefix.length());
            if (relative.isBlank() || !nameFilter.test(relative)) {
                continue;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                result.put(relative, readAll(in));
            } catch (IOException e) {
                continue;
            }
        }
    }

    /**
     * 从资源 URL 上截取 {@code classpathDir/} 之后的相对路径。
     *
     * <p>fat jar 内形如 {@code jar:nested:/x.jar/!BOOT-INF/classes!/skills-builtin/docx/SKILL.md}，
     * 文件系统形如 {@code file:/.../skills-builtin/docx/SKILL.md}，取最后一次出现的前缀之后部分即可。</p>
     *
     * <p>目录条目（URL 以 {@code classpathDir/} 结尾）截出的是空串，必须判为无效：
     * 否则调用方 {@code resolve("")} 会退回根目录，把目录当文件写入。</p>
     *
     * @param resource 资源
     * @param prefix   目录前缀（含尾斜杠）
     * @return 相对路径；不是文件条目时返回 {@code null}
     */
    private static String relativePath(Resource resource, String prefix) {
        try {
            URL url = resource.getURL();
            String text = url.toString();
            int idx = text.lastIndexOf(prefix);
            if (idx < 0) {
                return null;
            }
            String relative = text.substring(idx + prefix.length());
            int query = relative.indexOf('?');
            if (query >= 0) {
                relative = relative.substring(0, query);
            }
            if (relative.isBlank() || relative.endsWith("/")) {
                return null;
            }
            return relative;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 读取流内容，超过上限直接报错保护。
     *
     * @param in 输入流
     * @return 字节数组
     * @throws IOException 读取失败或超过上限
     */
    private static byte[] readAll(InputStream in) throws IOException {
        var out = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("单个 classpath 资源超过 " + MAX_ENTRY_BYTES + " 字节上限");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * 应用 jar 的定位结果。
     *
     * @param jarPath   外层 jar 的文件系统路径
     * @param nestedRoot jar 内嵌套根前缀，正常为 {@code BOOT-INF/classes!/}；本类自身来自依赖 jar 时为空串
     */
    private record JarLocation(String jarPath, String nestedRoot) {

        /**
         * 依据本类的 CodeSource 解析外层 jar 与嵌套根。
         *
         * <p>URL 形态在不同 Boot 版本间差异较大（{@code jar:file:/a.jar!/} 与
         * {@code jar:nested:/a.jar/!}、还可能叠加 {@code file:} 前缀与 {@code //} authority），
         * 因此不逐个匹配，而是剥掉所有「首个斜杠之前」的协议头与 authority。exploded 目录
         * 不含 {@code !}，返回 {@code null}。</p>
         *
         * @return 定位结果；非 jar 形态时返回 {@code null}
         */
        static JarLocation resolve() {
            CodeSource codeSource = ClasspathDirSupport.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            String text = codeSource.getLocation().toString();
            int firstBang = text.indexOf('!');
            if (firstBang < 0) {
                return null;
            }
            String jarPath = toFilePath(text.substring(0, firstBang));
            if (jarPath == null) {
                return null;
            }
            int secondBang = text.indexOf('!', firstBang + 1);
            String nestedRoot = secondBang < 0 ? "" : text.substring(firstBang + 1, secondBang + 1);
            return new JarLocation(jarPath, nestedRoot);
        }

        /**
         * 把 URL 头部还原为文件系统路径：剥掉全部协议头、authority 与尾斜杠。
         *
         * @param head URL 中首个 {@code !} 之前的部分
         * @return 文件系统路径；无法还原时返回 {@code null}
         */
        private static String toFilePath(String head) {
            String rest = head;
            while (true) {
                int colon = rest.indexOf(':');
                int slash = rest.indexOf('/');
                if (colon < 0 || (slash >= 0 && colon > slash)) {
                    break;
                }
                rest = rest.substring(colon + 1);
            }
            while (rest.startsWith("//")) {
                rest = rest.substring(2);
            }
            while (rest.endsWith("/")) {
                rest = rest.substring(0, rest.length() - 1);
            }
            rest = rest.replace("%20", " ");
            return rest.isBlank() ? null : rest;
        }
    }
}
