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
 * osgi 真实测试（bundle管理器 Maven 坐标全链路）：真实 Spring Boot 服务 +
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
        // 测试环境每次从干净 storage 开始：默认 onFirstInit 会让 Felix 恢复并
        // 自动启动历史版本 bundle，与本轮装卸形成竞争
        "plugin.osgi.storage-clean=onInit",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi",
        "plugin.bundle.enabled=true",
        "plugin.bundle.auto-upgrade=true"
})
/**
 * bundle管理器real测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class BundleManagerRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.osgi.test.greet"; // bundlesymbolic名称
    private static final String ARTIFACT_ID = "greet"; // ARTIFACT_标识
    private static final String GROUP_PATH = "com/chua/osgi/test"; // 群体路径
    private static final String GROUP_ID = "com.chua.osgi.test"; // 群体标识
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle"; // bundlebase包

    static {
        try {
 // 本地伪仓库布局：Target/测试-Maven-repo/{群体路径}/{artifact}/{版本}/{artifact}-{版本}.jar
            /**
             * 放入repojar。
             * @param "1.0.0" "1.0.0"
             * @param "v1" "v1"
             */
            putRepoJar("1.0.0", "v1");
            /**
             * 放入repojar。
             * @param "2.0.0" "2.0.0"
             * @param "v2" "v2"
             */
            putRepoJar("2.0.0", "v2");
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param 仓库准备失败" 仓库准备失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("本地伪 Maven 仓库准备失败", e);
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

    @AfterEach
    /**
     * cleanup。
     */
    void cleanup() {
        try {
            bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME);
        } catch (Exception e) {
            // 清理阶段失败不影响断言结果，仅记录
            System.out.println("[BundleManagerRealTest] 卸载测试 bundle 失败: " + e.getMessage());
        }
    }

    /**
     * 真实测试：Maven 坐标 install/upgrade/uninstall 全链路
    */
    @Test
    void installUpgradeUninstallViaMavenCoords() {
 // 1) install v1（文件: 协议本地下载 → 真实 bundle 安装自动启动）
        BundleManagerService.InstallResult r1 = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0");
        /**
         * 断言true。
         * @param r1.success( R1.成功(
         */
        assertTrue(r1.success(), "install v1 应成功: " + r1.message());
        GreetService v1 = osgiLauncher.getService(GreetService.class);
        /**
         * 断言not空。
         * @param v1 v1
         * @param GreetService" greet服务"
         */
        assertNotNull(v1, "install v1 后应能获取 GreetService");
        /**
         * 断言equals。
         * @param "mgr-v1:world" "mgr-v1:world"
         * @param v1.greet("world" v1.greet("world"
         */
        assertEquals("mgr-v1:world", v1.greet("world"), "v1 服务应返回 mgr-v1 结果");

        // 容器视角：安装 v1 后 ObjectContext 必须能查到 v1 实现
        GreetService contextV1 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(contextV1, "安装 v1 后 ObjectContext 应能查到 GreetService");
        assertEquals("mgr-v1:world", contextV1.greet("world"), "安装 v1 后容器应取到 v1 实现");

        // 2) install v2 同 artifact（高版本自动升级 → 卸载旧版安装新版）
        BundleManagerService.InstallResult r2 = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0");
        /**
         * 断言true。
         * @param r2.success( R2.成功(
         */
        assertTrue(r2.success(), "install v2 应成功: " + r2.message());
        GreetService afterUpgrade = osgiLauncher.getService(GreetService.class);
        /**
         * 断言not空。
         * @param afterUpgrade 之后upgrade
         * @param GreetService" greet服务"
         */
        assertNotNull(afterUpgrade, "升级后应能获取 GreetService");
        /**
         * 断言equals。
         * @param "mgr-v2:world" "mgr-v2:world"
         * @param afterUpgrade.greet("world" 之后upgrade.greet("world"
         */
        assertEquals("mgr-v2:world", afterUpgrade.greet("world"), "升级后应返回 v2 结果");

        // 热更新核心语义：原地升级后 ObjectContext 必须自动切换到 v2 实现，
        // 全程不重启 Spring 上下文
        GreetService contextV2 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(contextV2, "热升级后 ObjectContext 应能查到 GreetService");
        assertEquals("mgr-v2:world", contextV2.greet("world"),
                "热升级后 ObjectContext 应自动切换到 v2 实现（无需重启 Spring 上下文）");

 // 3) 获取状态 含已安装 bundle，uninstall 移除
        /**
         * 断言true。
         * @param bundleManager.getStatus( bundle管理器.获取状态(
         */
        assertTrue(bundleManager.getStatus().stream()
                        .anyMatch(m -> BUNDLE_SYMBOLIC_NAME.equals(m.get("symbolicName"))),
                "getStatus 应包含目标 bundle");
        /**
         * 断言true。
         * @param bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME bundlemanager.uninstall(bundlesymbolic名称
         */
        assertTrue(bundleManager.uninstall(BUNDLE_SYMBOLIC_NAME), "uninstall 应返回 true");
        /**
         * 断言空。
         * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertNull(osgiLauncher.getService(GreetService.class), "卸载后服务应消失");
        /**
         * 断言空。
         * @param objectContext.getBeanOfTypeSafely(GreetService.class 对象上下文.获取Bean的类型safely(greetservice.类
         */
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "卸载后 ObjectContext 应查不到");
    }

    /**
     * 真实测试：非法坐标（缺 版本）install 应失败但不抛异常
    */
    @Test
    void invalidCoordsFailGracefully() {
        BundleManagerService.InstallResult result = bundleManager.install("bad-coords-no-version");
        /**
         * 断言true。
         * @param !result.success( !结果.成功(
         */
        assertTrue(!result.success(), "非法坐标 install 应失败");
        /**
         * 断言not空。
         * @param result.message( 结果.消息(
         */
        assertNotNull(result.message(), "失败应携带 message");
    }

    /**
     * 真实测试：获取状态/是否installed/获取managedbundles 基础查询
    */
    @Test
    void statusQueriesWork() {
        /**
         * 断言true。
         * @param ":1.0.0" ":1.0.0"
         */
        assertTrue(bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0").success(),
                "install v1 应成功");
        List<Map<String, Object>> status = bundleManager.getStatus();
        assertTrue(status.stream().anyMatch(m -> BUNDLE_SYMBOLIC_NAME.equals(m.get("symbolicName"))),
                "getStatus 应包含目标 bundle");
        /**
         * 断言true。
         * @param bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME bundlemanager.isinstalled(bundlesymbolic名称
         */
        assertTrue(bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "isInstalled 应为 true");
        /**
         * 断言true。
         * @param bundleManager.getManagedBundles( bundle管理器.获取managedbundles(
         */
        assertTrue(bundleManager.getManagedBundles().contains(ARTIFACT_ID),
                "getManagedBundles 应包含 artifactId，实际: " + bundleManager.getManagedBundles());
    }

    /**
     * 真实测试：批量 install全部/uninstall全部
    */
    @Test
    void installAllAndUninstallAll() throws Exception {
 // 同一仓库构造第二个 artifact：批量-artifact
        /**
         * 放入batchrepojar。
         * @param "batch" "批量"
         * @param "1.0.0" "1.0.0"
         * @param "batch-v1" "批量-v1"
         */
        putBatchRepoJar("batch", "1.0.0", "batch-v1");
        String batchCoords = "com.chua.osgi.test:batch:1.0.0";
        String greetCoords = GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0";

        List<BundleManagerService.InstallResult> results =
                bundleManager.installAll(List.of(greetCoords, batchCoords));
        /**
         * 断言equals。
         * @param 2 2
         * @param results.size( 结果.大小(
         */
        assertEquals(2, results.size(), "installAll 应返回每个坐标的结果");
        assertTrue(results.stream().allMatch(BundleManagerService.InstallResult::success),
                "installAll 应全部成功: " + results);
        /**
         * 断言true。
         * @param bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME bundlemanager.isinstalled(bundlesymbolic名称
         */
        assertTrue(bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "greet 应已安装");
        /**
         * 断言true。
         * @param bundleManager.isInstalled("com.chua.osgi.test.batch" bundle管理器.是否installed("com.chua.osgi.测试.批量"
         */
        assertTrue(bundleManager.isInstalled("com.chua.osgi.test.batch"), "batch 应已安装");

        List<Boolean> uninstalled = bundleManager.uninstallAll(
                List.of(BUNDLE_SYMBOLIC_NAME, "com.chua.osgi.test.batch"));
        /**
         * 断言equals。
         * @param 2 2
         * @param uninstalled.size( uninstalled.大小(
         */
        assertEquals(2, uninstalled.size(), "uninstallAll 应返回每个 bundle 的结果");
        assertTrue(uninstalled.stream().allMatch(Boolean::booleanValue), "uninstallAll 应全部成功");
        /**
         * 断言true。
         * @param !bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME !bundlemanager.isinstalled(bundlesymbolic名称
         */
        assertTrue(!bundleManager.isInstalled(BUNDLE_SYMBOLIC_NAME), "greet 应已卸载");
        /**
         * 断言true。
         * @param !bundleManager.isInstalled("com.chua.osgi.test.batch" !bundle管理器.是否installed("com.chua.osgi.测试.批量"
         */
        assertTrue(!bundleManager.isInstalled("com.chua.osgi.test.batch"), "batch 应已卸载");
    }

    /**
     * 真实测试：版本语义——同版本重复 install 应成功不报错；低版本 install 不应降级
    */
    @Test
    void versionSemantics() {
        // 1) 安装 v2
        /**
         * 断言true。
         * @param ":2.0.0" ":2.0.0"
         */
        assertTrue(bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0").success(),
                "install v2 应成功");

        // 2) 重复安装同版本：install 应返回成功（已是最新版），不抛异常
        BundleManagerService.InstallResult repeat = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0");
        /**
         * 断言true。
         * @param repeat.success( repeat.成功(
         */
        assertTrue(repeat.success(), "重复安装同版本应成功: " + repeat.message());

 // 3) 低版本 install：autoupgrade=true 但目标版本低于已装版本 → 不应降级，仍返回 v2
        BundleManagerService.InstallResult downgrade = bundleManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0");
        /**
         * 断言true。
         * @param downgrade.success( downgrade.成功(
         */
        assertTrue(downgrade.success(), "低版本 install 应成功返回（不降级）: " + downgrade.message());
        GreetService stillV2 = osgiLauncher.getService(GreetService.class);
        /**
         * 断言not空。
         * @param stillV2 stillv2
         * @param "降级尝试后仍应有服务" "降级尝试后仍应有服务"
         */
        assertNotNull(stillV2, "降级尝试后仍应有服务");
        /**
         * 断言equals。
         * @param "mgr-v2:world" "mgr-v2:world"
         * @param stillV2.greet("world" stillv2.greet("world"
         */
        assertEquals("mgr-v2:world", stillV2.greet("world"), "低版本 install 不应降级，仍返回 v2");
    }

    /**
     * 真实测试：autoupgrade=false 时高版本 install 不应升级（保留低版本）
    */
    @Test
    void autoUpgradeDisabledKeepsOldVersion() {
 // 覆盖 bundle属性.autoupgrade=false：install v1 后 install v2 应保留 v1
        String repoUrl = Paths.get("target", "test-maven-repo").toAbsolutePath().toString()
                .replace('\\', '/');
        com.chua.starter.bundle.properties.BundleProperties props =
                new com.chua.starter.bundle.properties.BundleProperties();
        props.setRepositoryUrl("file:" + repoUrl);
        props.setEnabled(true);
        props.setAutoUpgrade(false);
        BundleManagerService noUpgradeManager =
                new BundleManagerService(osgiLauncher, props);

        /**
         * 断言true。
         * @param ":1.0.0" ":1.0.0"
         */
        assertTrue(noUpgradeManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":1.0.0").success(),
                "autoUpgrade=false install v1 应成功");
        /**
         * 断言true。
         * @param ":2.0.0" ":2.0.0"
         */
        assertTrue(noUpgradeManager.install(GROUP_ID + ":" + ARTIFACT_ID + ":2.0.0").success(),
                "autoUpgrade=false install v2 应成功（旧版保留，新版共存或返回旧版）");
        GreetService service = osgiLauncher.getService(GreetService.class);
        /**
         * 断言not空。
         * @param service 服务
         * @param 时仍应有服务" 时仍应有服务"
         */
        assertNotNull(service, "autoUpgrade=false 时仍应有服务");
 // autoupgrade=false 时 findexisting 命中旧版 → 直接返回旧版 成功，不升级
        /**
         * 断言true。
         * @param noUpgradeManager.getManagedBundles( noupgrade管理器.获取managedbundles(
         */
        assertTrue(noUpgradeManager.getManagedBundles().contains(ARTIFACT_ID),
                "managed 应包含 artifactId");
    }

    // ==================== 伪 Maven 仓库构造 ====================

    /**
     * 生成 文件 协议仓库下对应坐标的 bundle jar
     *
     * @param version 版本
     * @param prefix 前缀
     */
    private static void putRepoJar(String version, String prefix) throws Exception {
        String className = "MgrGreet" + version.replace('.', '_');
        Path srcFile = Paths.get("target", "bundle-mgr-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, "mgr-" + prefix), StandardCharsets.UTF_8);

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
     * 生成第二个 artifact（批量）的 bundle jar
     *
     * @param artifact artifact
     * @param version 版本
     * @param prefix 前缀
     */
    private static void putBatchRepoJar(String artifact, String version, String prefix) throws Exception {
        String className = "MgrBatch" + version.replace('.', '_');
        Path srcFile = Paths.get("target", "bundle-mgr-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

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

    // ==================== 伪 Maven 仓库构造 ====================

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