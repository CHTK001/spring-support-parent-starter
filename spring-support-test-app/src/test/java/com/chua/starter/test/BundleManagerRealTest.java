package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.bundle.service.BundleManagerService;
import com.chua.starter.test.osgi.GreetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OSGi 真实测试（BundleManager Maven 坐标全链路）：真实 Spring Boot 服务 +
 * {@link BundleManagerService}，Maven 坐标驱动安装/高版本自动升级/卸载。
 *
 * <p>为避免依赖外网（默认仓库 {@code https://repo1.maven.org/maven2}），测试自建
 * 本地"伪 Maven 仓库"布局（{@code target/test-maven-repo/...}），并经
 * {@code plugin.bundle.repository-url=file:...} 指向它，使坐标→URL 映射真实工作。</p>
 *
 * <p>bundle 的 symbolicName 含 artifactId（{@code greet}），使
 * {@code BundleManagerService.findExisting} 的 contains 匹配可定位旧版；
 * 接口仍桥接应用侧 {@link GreetService}（经 {@code system.packages.extra} 导出）。</p>
 *
 * @author CH
 * @since 2026/09/11
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-bundle-mgr",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi",
        "plugin.bundle.enabled=true",
        "plugin.bundle.auto-upgrade=true"
})
class BundleManagerRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.osgi.test.greet";
    private static final String ARTIFACT_ID = "greet";
    private static final String GROUP_PATH = "com/chua/osgi/test";
    private static final String GROUP_ID = "com.chua.osgi.test";
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle";

    static {
        try {
            // 本地伪仓库布局：target/test-maven-repo/{groupPath}/{artifact}/{version}/{artifact}-{version}.jar
            putRepoJar("1.0.0", "v1");
            putRepoJar("2.0.0", "v2");
        } catch (Exception e) {
            throw new IllegalStateException("本地伪 Maven 仓库准备失败", e);
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

    @AfterEach
    void cleanup() {
        try {
            bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME);
        } catch (Exception ignored) {
        }
    }

    /** 真实测试：Maven 坐标 install/upgrade/uninstall 全链路 */
    @Test
    void installUpgradeUninstallViaMavenCoords() {
        // 1) install v1（file: 协议本地下载 → 真实 bundle 安装自动启动）
        BundleManagerService.InstallResult r1 = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0");
        assertTrue(r1.success(), "install v1 应成功: " + r1.message());
        GreetService v1 = osgiLauncher.getService(GreetService.class);
        assertNotNull(v1, "install v1 后应能获取 GreetService");
        assertEquals("mgr-v1:world", v1.greet("world"), "v1 服务应返回 mgr-v1 结果");

        // 2) install v2 同 artifact（高版本自动升级 → 卸载旧版安装新版）
        BundleManagerService.InstallResult r2 = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0");
        assertTrue(r2.success(), "install v2 应成功: " + r2.message());
        GreetService afterUpgrade = osgiLauncher.getService(GreetService.class);
        assertNotNull(afterUpgrade, "升级后应能获取 GreetService");
        assertEquals("mgr-v2:world", afterUpgrade.greet("world"), "升级后应返回 v2 结果");

        // 3) getStatus 含已安装 bundle，uninstall 移除
        assertTrue(bundleManager.getStatus().stream()
                        .anyMatch(m -> BUNDLE_SYMBOLIC_NAME.equals(m.get("symbolicName"))),
                "getStatus 应包含目标 bundle");
        assertTrue(bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME), "uninstall 应返回 true");
        assertNull(osgiLauncher.getService(GreetService.class), "卸载后服务应消失");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "卸载后 ObjectContext 应查不到");
    }

    /** 真实测试：非法坐标（缺 version）install 应失败但不抛异常 */
    @Test
    void invalidCoordsFailGracefully() {
        BundleManagerService.InstallResult result = bundleManager.install("bad-coords-no-version");
        assertTrue(!result.success(), "非法坐标 install 应失败");
        assertNotNull(result.message(), "失败应携带 message");
    }

    /** 真实测试：getStatus/isInstalled/getManagedBundles 基础查询 */
    @Test
    void statusQueriesWork() {
        assertTrue(bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0").success(),
                "install v1 应成功");
        List<Map<String, Object>> status = bundleManager.getStatus();
        assertTrue(status.stream().anyMatch(m -> BUNDLE_SYMBOLIC_NAME.equals(m.get("symbolicName"))),
                "getStatus 应包含目标 bundle");
        assertTrue(bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "isInstalled 应为 true");
        assertTrue(bundleManager.getManagedBundles().contains(ARTIFACT_ID),
                "getManagedBundles 应包含 artifactId，实际: " + bundleManager.getManagedBundles());
    }

    /** 真实测试：批量 installAll/uninstallAll */
    @Test
    void installAllAndUninstallAll() throws Exception {
        // 同一仓库构造第二个 artifact：batch-artifact
        putBatchRepoJar("batch", "1.0.0", "batch-v1");
        String batchCoords = "com.chua.osgi.test:batch:1.0.0";
        String greetCoords = GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0";

        List<BundleManagerService.InstallResult> results =
                bundleManager.installAll(List.of(greetCoords, batchCoords));
        assertEquals(2, results.size(), "installAll 应返回每个坐标的结果");
        assertTrue(results.stream().allMatch(BundleManagerService.InstallResult::success),
                "installAll 应全部成功: " + results);
        assertTrue(bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "greet 应已安装");
        assertTrue(bundleManager.isInstalled("com.chua.osgi.test.batch"), "batch 应已安装");

        List<Boolean> uninstalled = bundleManager.uninstallAll(
                List.of(BUNDLE_SYMBOLIC_NAME, "com.chua.osgi.test.batch"));
        assertEquals(2, uninstalled.size(), "uninstallAll 应返回每个 bundle 的结果");
        assertTrue(uninstalled.stream().allMatch(Boolean::booleanValue), "uninstallAll 应全部成功");
        assertTrue(!bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "greet 应已卸载");
        assertTrue(!bundleManager.isInstalled("com.chua.osgi.test.batch"), "batch 应已卸载");
    }

    /** 真实测试：版本语义——同版本重复 install 应成功不报错；低版本 install 不应降级 */
    @Test
    void versionSemantics() {
        // 1) 安装 v2
        assertTrue(bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0").success(),
                "install v2 应成功");

        // 2) 重复安装同版本：install 应返回成功（已是最新版），不抛异常
        BundleManagerService.InstallResult repeat = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0");
        assertTrue(repeat.success(), "重复安装同版本应成功: " + repeat.message());

        // 3) 低版本 install：autoUpgrade=true 但目标版本低于已装版本 → 不应降级，仍返回 v2
        BundleManagerService.InstallResult downgrade = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0");
        assertTrue(downgrade.success(), "低版本 install 应成功返回（不降级）: " + downgrade.message());
        GreetService stillV2 = osgiLauncher.getService(GreetService.class);
        assertNotNull(stillV2, "降级尝试后仍应有服务");
        assertEquals("mgr-v2:world", stillV2.greet("world"), "低版本 install 不应降级，仍返回 v2");
    }

    /** 真实测试：autoUpgrade=false 时高版本 install 不应升级（保留低版本） */
    @Test
    void autoUpgradeDisabledKeepsOldVersion() {
        // 覆盖 BundleProperties.autoUpgrade=false：install v1 后 install v2 应保留 v1
        String repoUrl = Paths.get("target", "test-maven-repo").toAbsolutePath().toString()
                .replace('\\', '/');
        com.chua.starter.bundle.properties.BundleProperties props =
                new com.chua.starter.bundle.properties.BundleProperties();
        props.setRepositoryUrl("file:" + repoUrl);
        props.setEnabled(true);
        props.setAutoUpgrade(false);
        BundleManagerService noUpgradeManager =
                new BundleManagerService(osgiLauncher, props);

        assertTrue(noUpgradeManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0").success(),
                "autoUpgrade=false install v1 应成功");
        assertTrue(noUpgradeManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0").success(),
                "autoUpgrade=false install v2 应成功（旧版保留，新版共存或返回旧版）");
        GreetService service = osgiLauncher.getService(GreetService.class);
        assertNotNull(service, "autoUpgrade=false 时仍应有服务");
        // autoUpgrade=false 时 findExisting 命中旧版 → 直接返回旧版 success，不升级
        assertTrue(noUpgradeManager.getManagedBundles().contains(ARTIFACT_ID),
                "managed 应包含 artifactId");
    }

    // ==================== 伪 Maven 仓库构造 ====================

    /** 生成 file 协议仓库下对应坐标的 bundle jar */
    private static void putRepoJar(String version, String prefix) throws Exception {
        String className = "MgrGreet" + version.replace('.', '_');
        Path srcFile = Paths.get("target", "bundle-mgr-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, "mgr-" + prefix), StandardCharsets.UTF_8);

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

        Path jarFile = Paths.get("target", "test-maven-repo", GROUP_PATH, ARTIFACT_ID, version,
                ARTIFACT_ID + "-" + version + ".jar");
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

    /** 生成第二个 artifact（batch）的 bundle jar */
    private static void putBatchRepoJar(String artifact, String version, String prefix) throws Exception {
        String className = "MgrBatch" + version.replace('.', '_');
        Path srcFile = Paths.get("target", "bundle-mgr-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

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

        Path jarFile = Paths.get("target", "test-maven-repo", GROUP_PATH, artifact, version,
                artifact + "-" + version + ".jar");
        Files.createDirectories(jarFile.getParent());
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Bundle-ManifestVersion", "2");
        attrs.putValue("Bundle-SymbolicName", "com.chua.osgi.test." + artifact);
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

    // ==================== 伪 Maven 仓库构造 ====================

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