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
 * OSGi 真实测试（启动时自动安装/卸载）：独立 Spring 上下文通过配置
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
class BundleStartupInstallRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.osgi.test.startup";
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle";

    static {
        try {
            putStartupRepoJar();
        } catch (Exception e) {
            throw new IllegalStateException("启动测试伪仓库准备失败", e);
        }
    }

    @DynamicPropertySource
    static void localRepo(DynamicPropertyRegistry registry) {
        String repo = Paths.get("target", "test-maven-repo").toAbsolutePath().toString();
        registry.add("plugin.bundle.repository-url", () -> "file:" + repo.replace('\\', '/'));
    }

    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    @Autowired
    private BundleManagerService bundleManager;

    @Autowired
    private ObjectContext objectContext;

    /** 真实测试：context 启动后 install-at-startup 指定的 bundle 已自动安装且服务可用 */
    @Test
    void startupInstalledBundleAvailable() {
        assertTrue(osgiLauncher.isActive(), "Felix 框架应激活");
        GreetService service = osgiLauncher.getService(GreetService.class);
        assertNotNull(service, "启动自动安装后应能获取 GreetService");
        assertEquals("startup-v1:world", service.greet("world"), "启动自动安装的服务应返回 startup-v1 结果");

        GreetService fromContext = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(fromContext, "ObjectContext 应能查到启动自动安装的 GreetService");
        assertEquals("startup-v1:world", fromContext.greet("world"), "ObjectContext 查询结果应一致");

        assertTrue(bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "启动自动安装的 bundle 应已安装");
        assertTrue(bundleManager.getManagedBundles().contains("startup"), "managed 应包含 artifactId startup");
    }

    // ==================== 伪仓库构造 ====================

    /** 生成启动测试用的 bundle jar（坐标 com.chua.osgi.test:startup:1.0.0） */
    private static void putStartupRepoJar() throws Exception {
        String className = "StartupGreet1_0_0";
        Path srcFile = Paths.get("target", "bundle-mgr-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, "startup-v1"), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "bundle-mgr-classes", className);
        if (Files.exists(classOut)) {
            try (var walk = Files.walk(classOut)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (java.io.IOException e) {
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

    /** bundle 实现类源码：实现应用侧接口 + BundleActivator，start 时注册服务 */
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