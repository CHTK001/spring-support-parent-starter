package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.acceptance.BeanAcceptance;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiServiceDescriptor;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.test.osgi.GreetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * osgi 真实测试（Bean 优先级 / 扩展对象切换 / 验收）：
 * 验证 OSGI 服务投影为容器 Bean 时的三条关键语义。
 *
 * <ol>
 *   <li><b>优先级</b>：多个 bundle 注册同一服务类型时，{@code service.ranking} 高的胜出，
 *       且该 ranking 被贯通到 {@code BeanDefinition#getPriority()}，
 *       使容器既有的按优先级选优逻辑（{@code ObjectContext#getBeanOfType}）直接生效；</li>
 *   <li><b>扩展对象切换</b>：bundle 生命周期变化后，桥接快照版本推进、
 *       旧扩展对象失效，新查询返回新实例；</li>
 *   <li><b>验收</b>：验收器拒绝的服务不纳入快照，容器侧查不到。</li>
 * </ol>
 *
 * <p>shell 关闭（独立上下文，避免 SSH 8122 端口冲突）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-bridge-priority",
        // 测试环境每次从干净 storage 开始，避免历史 bundle 被恢复后与本轮装卸竞争
        "plugin.osgi.storage-clean=onInit",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.bridge.acceptance-enabled=true",
        "plugin.osgi.bridge.invalidate-on-bundle-lifecycle=true",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi"
})
class OsgiBeanBridgePriorityRealTest {

    /**
     * bundle 符号名。
     */
    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.bridge.bundle";

    /**
     * 应用侧服务接口。
     */
    private static final String GREET_SERVICE_INTERFACE = "com.chua.starter.test.osgi.GreetService";

    /**
     * bundle 实现类所在包。
     */
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bridge";

    /**
     * osgi 启动器。
     */
    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    /**
     * 桥接注册器。
     */
    @Autowired
    private OsgiBeanDefinitionRegister osgiBeanDefinitionRegister;

    /**
     * 对象上下文。
     */
    @Autowired
    private ObjectContext objectContext;

    /**
     * 每个用例后卸载本类安装的 bundle。
     */
    @AfterEach
    void cleanup() {
        try {
            osgiLauncher.getBundles().stream()
                    .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                    .forEach(OsgiBundle::uninstall);
        } catch (Exception ignored) {
            // 清理失败不掩盖用例自身结论
        }
    }

    /**
     * 真实测试：ranking 高的服务在容器侧被选为当前生效实例，且优先级已贯通到 Bean 定义。
     *
     * @throws Exception 构建 bundle jar 失败时抛出
     */
    @Test
    void higherRankingServiceWinsInContainer() throws Exception {
        // low 先注册，high 后注册——故意让"后注册的 ranking 更高"，
        // 以排除结果只是"按注册顺序取首个"的可能。
        // 版本号必须不同：OSGI 要求 Bundle-SymbolicName + Bundle-Version 唯一，
        // 同符号名同版本的第二个 bundle 会被框架拒绝安装。
        File lowJar = buildBundleJar("GreetLow", "low", "1.0.0", 1L);
        File highJar = buildBundleJar("GreetHigh", "high", "2.0.0", 100L);

        osgiLauncher.installBundle("file:" + lowJar.getAbsolutePath());
        osgiLauncher.installBundle("file:" + highJar.getAbsolutePath());

        // 1) 启动器侧：getService 应取 ranking 最高者，而非注册顺序首个
        GreetService winner = osgiLauncher.getService(GreetService.class);
        assertNotNull(winner, "应能取到 GreetService");
        assertEquals("high:world", winner.greet("world"),
                "getService 应选中 service.ranking 更高的服务（实际 " + winner.greet("world") + "）");

        List<GreetService> all = osgiLauncher.getServices(GreetService.class);
        assertEquals(2, all.size(), "两个版本的服务应都可见");
        assertEquals("high:world", all.getFirst().greet("world"),
                "getServices 结果应按 ranking 降序，首项为高优先级者");

        // 2) 描述符侧：ranking 被正确读出
        List<OsgiServiceDescriptor> descriptors = osgiLauncher.getServiceDescriptors().stream()
                .filter(d -> GREET_SERVICE_INTERFACE.equals(d.typeName()))
                .toList();
        assertEquals(2, descriptors.size(), "应枚举到 2 个 GreetService 描述符");
        assertEquals(100L, descriptors.getFirst().ranking(), "描述符首项 ranking 应为 100");

        // 3) 容器侧：ranking 贯通到 BeanDefinition.priority，
        //    ObjectContext#getBeanOfType 依此选优，无需容器侧任何定制
        GreetService fromContext = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(fromContext, "ObjectContext 应能查到 GreetService");
        assertEquals("high:world", fromContext.greet("world"),
                "容器侧应按 Bean 优先级选出 ranking 更高的服务");

        long maxPriority = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE)
                .stream()
                .mapToLong(com.chua.common.support.objects.definition.BeanDefinition::getPriority)
                .max()
                .orElse(-1L);
        assertEquals(100L, maxPriority, "service.ranking 应已贯通为 Bean 定义的 priority");
    }

    /**
     * 真实测试：bundle 卸载后扩展对象随之切换（快照版本推进 + 旧定义失效 + 新实例可见）。
     *
     * @throws Exception 构建 bundle jar 失败时抛出
     */
    @Test
    void extensionObjectSwitchesOnBundleLifecycle() throws Exception {
        File v1Jar = buildBundleJar("GreetSwV1", "swv1", "1.0.0", 0L);
        osgiLauncher.installBundle("file:" + v1Jar.getAbsolutePath());

        GreetService first = osgiLauncher.getService(GreetService.class);
        assertNotNull(first, "安装后应能取到服务");
        assertEquals("swv1:world", first.greet("world"), "初始应返回 v1 结果");

        com.chua.common.support.objects.definition.BeanDefinition before =
                osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE)
                        .iterator().next();
        long versionBefore = osgiBeanDefinitionRegister.getFrameworkVersion();
        assertTrue(before.isAvailable(), "投影出的扩展对象应处于可用状态");

        // 卸载 v1 → 桥接定义应消失
        osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
        assertTrue(osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                "卸载后桥接定义应消失");
        assertEquals(null, objectContext.getBeanOfTypeSafely(GreetService.class),
                "卸载后容器侧应查不到该服务");
        assertNotEquals(versionBefore, osgiBeanDefinitionRegister.getFrameworkVersion(),
                "生命周期变化应推进快照版本号");

        // 安装 v2 → 扩展对象切换为新实现
        File v2Jar = buildBundleJar("GreetSwV2", "swv2", "2.0.0", 0L);
        osgiLauncher.installBundle("file:" + v2Jar.getAbsolutePath());

        GreetService second = osgiLauncher.getService(GreetService.class);
        assertNotNull(second, "安装 v2 后应能取到服务");
        assertEquals("swv2:world", second.greet("world"), "扩展对象应已切换为 v2 实现");
        assertEquals("swv2:world", objectContext.getBeanOfTypeSafely(GreetService.class).greet("world"),
                "容器侧查询结果应同步切换为 v2");
    }

    /**
     * 真实测试：验收器拒绝的服务不纳入桥接快照，容器侧查不到。
     *
     * @throws Exception 构建 bundle jar 失败时抛出
     */
    @Test
    void rejectedByAcceptanceIsNotBridged() throws Exception {
        // 前置：注册一个只拒绝本用例服务的验收器
        BeanAcceptance rejecting = new BeanAcceptance() {
            @Override
            public boolean isSupport(com.chua.common.support.objects.definition.BeanDefinition beanDefinition) {
                return true;
            }

            @Override
            public BeanAcceptance.AcceptanceResult accept(
                    com.chua.common.support.objects.definition.BeanDefinition beanDefinition) {
                return BeanAcceptance.AcceptanceResult.reject("用例注入的拒绝验收器");
            }
        };
        TestAcceptanceRegistry.register(rejecting);
        osgiBeanDefinitionRegister.addAcceptance(rejecting);
        try {
            File jar = buildBundleJar("GreetRejected", "rejected", "1.0.0", 0L);
            osgiLauncher.installBundle("file:" + jar.getAbsolutePath());

            // 服务在 OSGI 侧确实存在
            assertNotNull(osgiLauncher.getService(GreetService.class), "OSGI 侧服务应存在");
            // 但容器侧不应桥接
            assertTrue(osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                    "被验收拒绝的服务不应进入桥接快照");
            assertEquals(null, objectContext.getBeanOfTypeSafely(GreetService.class),
                    "被验收拒绝的服务不应被容器查到");
            assertFalse(osgiBeanDefinitionRegister.getRejectedSnapshots().isEmpty(),
                    "应记录验收拒绝原因");
        } finally {
            osgiBeanDefinitionRegister.removeAcceptance(rejecting);
            TestAcceptanceRegistry.unregister(rejecting);
        }
    }

    // ==================== 真实 bundle jar 生成 ====================

    /**
     * 动态编译 bundle 实现类并打包为合法 osgi bundle jar。
     *
     * @param className 类名称
     * @param prefix    返回值前缀
     * @param version   版本
     * @param ranking   服务优先级（{@code service.ranking}）
     * @return 构建出的 bundle jar
     * @throws Exception 编译或打包失败时抛出
     */
    private static File buildBundleJar(String className, String prefix, String version, long ranking)
            throws Exception {
        Path srcFile = Paths.get("target", "osgi-bridge-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix, ranking), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "osgi-bridge-classes", className);
        if (Files.exists(classOut)) {
            try (var walk = Files.walk(classOut)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
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

        File jarFile = new File("target/" + className + "-bundle.jar");
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Bundle-ManifestVersion", "2");
        attrs.putValue("Bundle-SymbolicName", BUNDLE_SYMBOLIC_NAME);
        attrs.putValue("Bundle-Version", version);
        attrs.putValue("Bundle-Activator", BUNDLE_BASE_PACKAGE + "." + className);
        attrs.putValue("Import-Package", "org.osgi.framework, com.chua.starter.test.osgi");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
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
        return jarFile;
    }

    /**
     * bundle 实现类源码：以指定 ranking 注册服务。
     *
     * @param className 类名称
     * @param prefix    返回值前缀
     * @param ranking   服务优先级
     * @return 源码文本
     */
    private static String bundleSource(String className, String prefix, long ranking) {
        return "package " + BUNDLE_BASE_PACKAGE + ";\n"
                + "import com.chua.starter.test.osgi.GreetService;\n"
                + "import org.osgi.framework.BundleActivator;\n"
                + "import org.osgi.framework.BundleContext;\n"
                + "import java.util.Hashtable;\n"
                + "public class " + className + " implements GreetService, BundleActivator {\n"
                + "    @Override\n"
                + "    public String greet(String name) {\n"
                + "        return \"" + prefix + ":\" + name;\n"
                + "    }\n"
                + "    @Override\n"
                + "    public void start(BundleContext context) throws Exception {\n"
                + "        Hashtable<String, Object> props = new Hashtable<>();\n"
                + "        props.put(\"service.ranking\", " + ranking + "L);\n"
                + "        context.registerService(GreetService.class.getName(), this, props);\n"
                + "    }\n"
                + "    @Override\n"
                + "    public void stop(BundleContext context) throws Exception {\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 用例内验收器登记表。
     * <p>
     * 保留仅为记录用例曾注册过哪些验收器，便于失败时定位；
     * 实际生效路径是 {@link OsgiBeanDefinitionRegister#addAcceptance}。
     * </p>
     */
    static final class TestAcceptanceRegistry {

        /**
         * 用例注册的验收器。
         */
        private static final List<BeanAcceptance> EXTRA = new CopyOnWriteArrayList<>();

        /**
         * 工具类禁止实例化。
         */
        private TestAcceptanceRegistry() {
        }

        /**
         * 登记验收器。
         *
         * @param acceptance 验收器
         */
        static void register(BeanAcceptance acceptance) {
            EXTRA.add(acceptance);
        }

        /**
         * 移除验收器。
         *
         * @param acceptance 验收器
         */
        static void unregister(BeanAcceptance acceptance) {
            EXTRA.remove(acceptance);
        }
    }
}
