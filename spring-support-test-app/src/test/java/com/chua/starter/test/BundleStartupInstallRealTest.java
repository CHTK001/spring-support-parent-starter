package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.bundle.service.BundleManagerService;
import com.chua.starter.test.osgi.GreetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.FileOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * osgi 真实测试（启动时自动安装/卸载）：独立 Spring 上下文通过配置
 * {@code plugin.bundle.install-at-startup / uninstall-at-startup}，
 * 在 Spring 初始化完成后自动安装指定坐标的 bundle。
 *
 * <p>{@code uninstall-at-startup} 先执行（容错不存在的 bundle），随后
 * {@code install-at-startup} 安装；file: 协议本地伪仓库，避免依赖外网。</p>
 *
 * @author CH
 * @since 2026/09/11
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-bundle-startup",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi",
        "plugin.bundle.enabled=true",
        "plugin.bundle.auto-upgrade=true",
        "plugin.bundle.install-at-startup[0]=com.chua.osgi.test:startup:1.0.0",
        "plugin.bundle.uninstall-at-startup[0]=com.chua.osgi.test.nonexistent"
})
/**
 * bundlestartupinstallreal测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class BundleStartupInstallRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.osgi.test.startup"; // bundlesymbolic名称
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle"; // bundlebase包

    static {
        try {
            /**
             * 放入startuprepojar。
             */
            putStartupRepoJar();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "启动测试伪仓库准备失败" "启动测试伪仓库准备失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("启动测试伪仓库准备失败", e);
        }
    }

    @DynamicPropertySource
    /**
     * 本地repo。
     * @param registry registry
     */
    static void localRepo(DynamicPropertyRegistry registry) {
        String repo = Paths.get("target", "test-maven-repo").toAbsolutePath().toString();
        registry.add("plugin.bundle.repository-url", () -> "file:" + repo.replace('\\', '/'));
    }

    @Autowired
    private FelixOsgiLauncher osgiLauncher; // osgilauncher

    @Autowired
    private BundleManagerService bundleManager; // bundle管理器

    @Autowired
    private ObjectContext objectContext; // 对象上下文

    /**
     * 真实测试：上下文 启动后 install-at-startup 指定的 bundle 已自动安装且服务可用
    */
    @Test
    void startupInstalledBundleAvailable() {
        /**
         * 断言true。
         * @param osgiLauncher.isActive( osgilauncher.是否活跃(
         */
        assertTrue(osgiLauncher.isActive(), "Felix 框架应激活");
        GreetService service = osgiLauncher.getService(GreetService.class);
        /**
         * 断言not空。
         * @param service 服务
         * @param GreetService" greet服务"
         */
        assertNotNull(service, "启动自动安装后应能获取 GreetService");
        /**
         * 断言equals。
         * @param "startup-v1:world" "startup-v1:world"
         * @param service.greet("world" 服务.greet("world"
         */
        assertEquals("startup-v1:world", service.greet("world"), "启动自动安装的服务应返回 startup-v1 结果");

        GreetService fromContext = objectContext.getBeanOfTypeSafely(GreetService.class);
        /**
         * 断言not空。
         * @param fromContext 从上下文
         * @param GreetService" greet服务"
         */
        assertNotNull(fromContext, "ObjectContext 应能查到启动自动安装的 GreetService");
        /**
         * 断言equals。
         * @param "startup-v1:world" "startup-v1:world"
         * @param fromContext.greet("world" 从上下文.greet("world"
         */
        assertEquals("startup-v1:world", fromContext.greet("world"), "ObjectContext 查询结果应一致");

        /**
         * 断言true。
         * @param bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME bundlemanager.isinstalled(bundlesymbolic名称
         */
        assertTrue(bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "启动自动安装的 bundle 应已安装");
        /**
         * 断言true。
         * @param bundleManager.getManagedBundles( bundle管理器.获取managedbundles(
         */
        assertTrue(bundleManager.getManagedBundles().contains("startup"), "managed 应包含 artifactId startup");
    }

    // ==================== 伪仓库构造 ====================

    /**
     * 生成启动测试用的 bundle jar（坐标 com.chua.osgi.测试:startup:1.0.0）
    */
    private static void putStartupRepoJar() throws Exception {
        String className = "StartupGreet1_0_0";
        Path srcFile = Paths.get("target", "bundle-mgr-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, "startup-v1"), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "bundle-mgr-classes", className);
        if (Files.exists(classOut)) {
            /**
             * 尝试。
             * @param Files.walk(classOut 文件.walk(类出
             */
            try (var walk = Files.walk(classOut)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (java.io.IOException e) {
                        /**
                         * uncheckedio异常。
                         * @param e e
                         * @return uncheckedio异常的结果
                         */
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        Files.createDirectories(classOut);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                "-d", classOut.toString(),
                "-classpath", System.getProperty("java.class.path"),
                srcFile.toString());
        if (result != 0) {
            /**
             * illegal状态异常。
             * @param className 类名称
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("bundle 实现类编译失败: " + className);
        }

        Path jarFile = Paths.get("target", "test-maven-repo", "com", "chua", "osgi", "test",
                "startup", "1.0.0", "startup-1.0.0.jar");
        Files.createDirectories(jarFile.getParent());
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Bundle-ManifestVersion", "2");
        attrs.putValue("Bundle-SymbolicName", BUNDLE_SYMBOLIC_NAME);
        attrs.putValue("Bundle-Version", "1.0.0");
        attrs.putValue("Bundle-Activator", BUNDLE_BASE_PACKAGE + "." + className);
        attrs.putValue("Import-Package", "org.osgi.framework, com.chua.starter.test.osgi");
        /**
         * 尝试。
         * @param FileOutputStream(jarFile.toFile( 文件输出流(jarfile.tofile(
         */
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()), manifest)) {
            /**
             * 尝试。
             * @param Files.walk(classOut 文件.walk(类出
             */
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
     * bundle 实现类源码：实现应用侧接口 + bundleactivator，启动 时注册服务
     *
     * @param className 类名称
     * @param prefix 前缀
     * @return bundle源的结果
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
}