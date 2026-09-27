package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.starter.bundle.service.BundleManagerService;
import com.chua.starter.test.osgi.GreetService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实测试：Maven 坐标经 <b>HTTP 远程仓库</b>拉取的插件部署全链路。
 *
 * <p>与 {@code BundleManagerRealTest} 互补：后者用 {@code file:} 协议指向本地伪仓库，
 * 本用例在同一坐标→URL→安装→桥接→容器链路上换成 {@code http://} 传输，
 * 覆盖真实生产形态（私有 Maven 仓库以 HTTP 暴露构件）：</p>
 * <pre>
 * BundleManagerService.install(坐标)
 *   → toMavenUrl 拼出 http://127.0.0.1:{port}/{groupId路径}/{artifactId}/{version}/{artifactId}-{version}.jar
 *   → Felix 经 HTTP 读取构件流并安装/启动
 *   → 桥接进 ObjectContext：v1 可取
 *   → 高版本坐标 → 原地热升级 → 容器切到 v2
 *   → 卸载 → 容器下线
 * </pre>
 *
 * <p>HTTP 服务端由 JDK 内置 {@link HttpServer} 提供（不依赖外网），
 * 并统计命中次数，用于证明构件确实经 HTTP 拉取而非本地缓存。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-http-repo",
        "plugin.osgi.storage-clean=onInit",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi",
        "plugin.bundle.enabled=true",
        "plugin.bundle.auto-upgrade=true"
})
class BundleManagerHttpRepoRealTest {

    /**
     * 坐标 groupId。
     */
    private static final String GROUP_ID = "com.chua.osgi.test";

    /**
     * groupId 对应的仓库路径。
     */
    private static final String GROUP_PATH = "com/chua/osgi/test";

    /**
     * 坐标 artifactId（与 file 协议用例区分，避免同名残留互相干扰）。
     */
    private static final String ARTIFACT_ID = "greet-http";

    /**
     * bundle 符号名。
     */
    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.osgi.test.greet.http";

    /**
     * bundle 实现类包名。
     */
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle.http";

    /**
     * HTTP 仓库根目录（本地伪仓库布局）。
     */
    private static final Path REPO_ROOT = Paths.get("target", "test-http-maven-repo").toAbsolutePath();

    /**
     * 构件被 HTTP 拉取的命中次数。
     */
    private static final AtomicInteger HTTP_HITS = new AtomicInteger();

    /**
     * 测试用 HTTP 仓库服务。
     */
    private static final HttpServer SERVER;

    static {
        try {
            putRepoJar("1.0.0", "http-v1");
            putRepoJar("2.0.0", "http-v2");
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/", BundleManagerHttpRepoRealTest::serveRepoFile);
            // 守护线程执行器：避免 HttpServer 的非守护线程阻塞测试 JVM 退出
            SERVER.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "test-http-repo");
                thread.setDaemon(true);
                return thread;
            }));
            SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> SERVER.stop(0), "test-http-repo-stop"));
        } catch (Exception e) {
            throw new IllegalStateException("HTTP 伪 Maven 仓库准备失败", e);
        }
    }

    @Autowired
    private BundleManagerService bundleManager;

    @Autowired
    private ObjectContext objectContext;

    /**
     * 将 HTTP 仓库地址注入为构件仓库。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void repositoryUrl(DynamicPropertyRegistry registry) {
        registry.add("plugin.bundle.repository-url",
                () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    /**
     * 清理本次安装的 bundle，避免污染同 JVM 内的其他用例。
     */
    @AfterEach
    void cleanup() {
        try {
            bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME);
        } catch (Exception ignored) {
            // 未安装时忽略
        }
    }

    /**
     * 真实测试：HTTP 仓库拉取 → 安装 → 容器可用 → 热升级 → 容器切换 → 卸载 → 下线。
     */
    @Test
    void installAndUpgradeOverHttpRepository() {
        int hitsBefore = HTTP_HITS.get();

        // 1) HTTP 仓库安装 v1 → 容器可取到 v1
        BundleManagerService.InstallResult r1 = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0");
        assertTrue(r1.success(), "HTTP 仓库安装 v1 应成功: " + r1.message());
        assertTrue(HTTP_HITS.get() > hitsBefore, "构件应经 HTTP 拉取（命中次数未增长）");

        GreetService contextV1 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(contextV1, "HTTP 安装 v1 后 ObjectContext 应能查到插件服务");
        assertEquals("http-v1:world", contextV1.greet("world"), "容器应取到经 HTTP 安装的 v1 实现");

        // 2) 高版本坐标 → 原地热升级 → 容器切换到 v2
        int hitsBeforeUpgrade = HTTP_HITS.get();
        BundleManagerService.InstallResult r2 = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0");
        assertTrue(r2.success(), "HTTP 仓库升级 v2 应成功: " + r2.message());
        assertTrue(HTTP_HITS.get() > hitsBeforeUpgrade, "升级应再次经 HTTP 拉取构件");

        GreetService contextV2 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(contextV2, "热升级后 ObjectContext 应仍能查到插件服务");
        assertEquals("http-v2:world", contextV2.greet("world"),
                "热升级后容器应自动切换到 v2 实现（无需重启 Spring 上下文）");

        // 3) 卸载 → 容器下线
        assertTrue(bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME), "卸载应成功");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class),
                "卸载后 ObjectContext 应查不到插件服务");
    }

    /**
     * 以本地伪仓库根目录为根，按请求路径返回构件文件。
     *
     * @param exchange HTTP 交换对象
     * @throws IOException 读写响应失败时抛出
     */
    private static void serveRepoFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Path target = REPO_ROOT.resolve(path.startsWith("/") ? path.substring(1) : path).normalize();
        if (!target.startsWith(REPO_ROOT) || !Files.isRegularFile(target)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] body = Files.readAllBytes(target);
        HTTP_HITS.incrementAndGet();
        exchange.getResponseHeaders().add("Content-Type", "application/java-archive");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /**
     * 生成指定版本的 bundle jar 并放入本地伪 Maven 仓库布局。
     *
     * @param version 版本
     * @param prefix  greet 返回前缀
     * @throws Exception 编译或打包失败时抛出
     */
    private static void putRepoJar(String version, String prefix) throws Exception {
        String className = "HttpGreet" + version.replace('.', '_');
        Path srcFile = Paths.get("target", "http-repo-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "http-repo-classes", className);
        deleteRecursively(classOut);
        Files.createDirectories(classOut);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                "-d", classOut.toString(),
                "-classpath", System.getProperty("java.class.path"),
                srcFile.toString());
        if (result != 0) {
            throw new IllegalStateException("bundle 实现类编译失败: " + className);
        }

        Path jarFile = REPO_ROOT.resolve(GROUP_PATH).resolve(ARTIFACT_ID).resolve(version)
                .resolve(ARTIFACT_ID + "-" + version + ".jar");
        Files.createDirectories(jarFile.getParent());
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Bundle-ManifestVersion", "2");
        attrs.putValue("Bundle-SymbolicName", BUNDLE_SYMBOLIC_NAME);
        attrs.putValue("Bundle-Version", version);
        attrs.putValue("Bundle-Activator", BUNDLE_BASE_PACKAGE + "." + className);
        attrs.putValue("Import-Package", "org.osgi.framework, com.chua.starter.test.osgi");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()), manifest)) {
            try (var walk = Files.walk(classOut)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    if (p.toString().endsWith(".class")) {
                        String entry = classOut.relativize(p).toString().replace('\\', '/');
                        jos.putNextEntry(new JarEntry(entry));
                        jos.write(Files.readAllBytes(p));
                        jos.closeEntry();
                    }
                }
            }
        }
    }

    /**
     * 生成 bundle 实现类源码：实现应用侧接口并在 activator 中注册服务。
     *
     * @param className 实现类名
     * @param prefix    greet 返回前缀
     * @return 源码文本
     */
    private static String bundleSource(String className, String prefix) {
        return "package " + BUNDLE_BASE_PACKAGE + ";\n"
                + "import com.chua.starter.test.osgi.GreetService;\n"
                + "import org.osgi.framework.BundleActivator;\n"
                + "import org.osgi.framework.BundleContext;\n"
                + "public class " + className + " implements GreetService, BundleActivator {\n"
                + "    @Override\n"
                + "    public String greet(String name) {\n"
                + "        return \"" + prefix + ":\" + name;\n"
                + "    }\n"
                + "    @Override\n"
                + "    public void start(BundleContext context) throws Exception {\n"
                + "        context.registerService(GreetService.class.getName(), this, null);\n"
                + "    }\n"
                + "    @Override\n"
                + "    public void stop(BundleContext context) throws Exception {\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 递归删除目录或文件。
     *
     * @param path 目标路径
     */
    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
