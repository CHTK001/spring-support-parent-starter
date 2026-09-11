package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.definition.BeanDefinition;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.test.osgi.GreetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OSGi 真实测试（Bean 切换）：真实 Spring Boot 服务 + Apache Felix 框架，
 * 安装**真实 bundle jar**（动态编译的 BundleActivator，启动时经 OSGi 服务注册表
 * 注册应用侧接口 {@link GreetService} 服务），验证经
 * {@link OsgiBeanDefinitionRegister} 桥接查询的 Bean 随 bundle 生命周期正常切换：
 * <pre>
 * 安装 v1 bundle → GreetService Bean 出现（返回 v1 结果）
 * 卸载 v1 bundle → GreetService Bean 消失（OSGi 服务随 bundle 注销）
 * 安装 v2 bundle → GreetService Bean 切换（返回 v2 结果，同接口不同实现）
 * </pre>
 *
 * <p>关键技术点：bundle 内实现类通过 {@code Import-Package: com.chua.starter.test.osgi}
 * 引用应用侧接口，框架经 {@code plugin.osgi.framework-properties.org.osgi.framework.system.packages.extra}
 * 导出该包，使 bundle 类加载的接口与应用侧为同一 Class（类身份一致），
 * 从而 {@code launcher.getService(type)} 的 {@code type.cast(service)} 成功。</p>
 *
 * <p>shell 关闭（独立上下文，避免 SSH 8122 端口冲突）。</p>
 *
 * @author CH
 * @since 2026/09/10
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-bean-switch",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi"
})
class OsgiBeanSwitchRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.greet.bundle";
    private static final String GREET_SERVICE_INTERFACE = "com.chua.starter.test.osgi.GreetService";
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle";

    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    @Autowired
    private OsgiBeanDefinitionRegister osgiBeanDefinitionRegister;

    @Autowired
    private ObjectContext objectContext;

    /** 真实测试：安装 → Bean 出现；卸载 → Bean 消失；换版本 → Bean 切换 */
    @Test
    void greetBeanSwitchesWithBundleLifecycle() throws Exception {
        // 1) 初始：无 GreetService 服务与 Bean
        assertNull(osgiLauncher.getService(GreetService.class), "初始不应存在 OSGi 服务");
        assertTrue(osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                "初始不应存在 GreetService Bean");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "初始 ObjectContext 不应查到 GreetService");

        // 2) 编译并安装 v1 bundle → 服务注册，Bean 出现
        File v1Jar = buildBundleJar("GreetServiceV1", "v1", "1.0.0");
        osgiLauncher.installBundle("file:" + v1Jar.getAbsolutePath());
        GreetService v1Service = osgiLauncher.getService(GreetService.class);
        assertNotNull(v1Service, "安装 v1 后应能获取 GreetService");
        assertEquals("v1:world", v1Service.greet("world"), "v1 服务应返回 v1 结果");

        Collection<BeanDefinition> defs1 = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        assertEquals(1, defs1.size(), "安装 v1 后应查询到 1 个 GreetService Bean");
        GreetService v1Bean = (GreetService) defs1.iterator().next().getBean();
        assertEquals("v1:world", v1Bean.greet("world"), "v1 Bean 应返回 v1 结果");

        // 一致性：ObjectContext 与 osgiLauncher 均返回 v1
        GreetService fromContextV1 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(fromContextV1, "安装 v1 后 ObjectContext 应能查到 GreetService");
        assertEquals("v1:world", fromContextV1.greet("world"), "ObjectContext 查询结果应等于 osgiLauncher 的 v1 结果");

        // 3) 卸载 v1 bundle → 服务注销，Bean 消失
        osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
        assertNull(osgiLauncher.getService(GreetService.class), "卸载 v1 后 OSGi 服务应消失");
        assertTrue(osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                "卸载 v1 后 GreetService Bean 应消失");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "卸载 v1 后 ObjectContext 应查不到 GreetService");

        // 4) 编译并安装 v2 bundle（同 symbolicName 不同版本）→ Bean 切换为新实现
        File v2Jar = buildBundleJar("GreetServiceV2", "v2", "2.0.0");
        osgiLauncher.installBundle("file:" + v2Jar.getAbsolutePath());
        GreetService v2Service = osgiLauncher.getService(GreetService.class);
        assertNotNull(v2Service, "安装 v2 后应能获取 GreetService");
        assertEquals("v2:world", v2Service.greet("world"), "v2 服务应返回 v2 结果（Bean 切换）");

        Collection<BeanDefinition> defs2 = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        assertEquals(1, defs2.size(), "安装 v2 后应查询到 1 个 GreetService Bean");
        GreetService v2Bean = (GreetService) defs2.iterator().next().getBean();
        assertEquals("v2:world", v2Bean.greet("world"), "v2 Bean 应返回 v2 结果（Bean 切换）");

        // 一致性：ObjectContext 与 osgiLauncher 均返回 v2
        GreetService fromContextV2 = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(fromContextV2, "安装 v2 后 ObjectContext 应能查到 GreetService");
        assertEquals("v2:world", fromContextV2.greet("world"), "ObjectContext 查询结果应等于 osgiLauncher 的 v2 结果");

        // 5) 清理：卸载 v2
        osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
        assertNull(osgiLauncher.getService(GreetService.class), "卸载 v2 后 OSGi 服务应消失");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "卸载 v2 后 ObjectContext 应查不到 GreetService");
    }

    /** 真实测试：同一 symbolicName 的两个不同版本 bundle 可共存，服务/Bean 均为双份，卸载按版本精确处理 */
    @Test
    void twoVersionsCoexistAndUninstallOne() throws Exception {
        // 1) 同时安装 v1 与 v2（同 symbolicName，不同 version）
        File v1Jar = buildBundleJar("GreetServiceV1", "v1", "1.0.0");
        File v2Jar = buildBundleJar("GreetServiceV2", "v2", "2.0.0");
        osgiLauncher.installBundle("file:" + v1Jar.getAbsolutePath());
        osgiLauncher.installBundle("file:" + v2Jar.getAbsolutePath());

        // 2) 两个版本 bundle 均应存在（按 symbolicName 统计）
        List<com.chua.common.support.osgi.OsgiBundle> bundles = osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .toList();
        assertEquals(2, bundles.size(), "同 symbolicName 的两个版本 bundle 应共存，实际: " + bundles.size());
        List<String> versions = bundles.stream().map(com.chua.common.support.osgi.OsgiBundle::getVersion).sorted().toList();
        assertEquals(List.of("1.0.0", "2.0.0"), versions, "两版本应分别为 1.0.0 与 2.0.0");

        // 3) 服务注册表应同时提供两个 GreetService 实例（v1 + v2）
        List<GreetService> services = osgiLauncher.getServices(GreetService.class);
        assertEquals(2, services.size(), "两版本共存时服务应有两个，实际: " + services.size());
        Set<String> outputs = services.stream().map(s -> s.greet("world")).collect(Collectors.toSet());
        assertEquals(Set.of("v1:world", "v2:world"), outputs, "两版本服务应分别返回 v1/v2 结果");

        // 4) Bean 桥接：两版本共存时 Bean 定义应为双份
        Collection<BeanDefinition> defs = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        assertEquals(2, defs.size(), "两版本共存时 Bean 应为双份，实际: " + defs.size());

        // ObjectContext 一致性：两版本共存时 ObjectContext 也应查到相同的 2 个服务（与 osgiLauncher 一致）
        Collection<GreetService> fromContext = objectContext.getBeanOfTypeCollection(GreetService.class);
        assertEquals(2, fromContext.size(), "ObjectContext 双版本共存时也应查到 2 个 GreetService，实际: " + fromContext.size());
        Set<String> contextOutputs = fromContext.stream().map(s -> s.greet("world")).collect(Collectors.toSet());
        assertEquals(Set.of("v1:world", "v2:world"), contextOutputs,
                "ObjectContext 查询结果应与 osgiLauncher 一致（v1+v2）");

        // 5) 主动基于 bundle 对象精确卸载 v1（1.0.0）→ 仅 v1 消失，v2 保留
        bundles.stream()
                .filter(b -> "1.0.0".equals(b.getVersion()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("应存在 v1 bundle"))
                .uninstall();
        List<com.chua.common.support.osgi.OsgiBundle> remaining = osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .toList();
        assertEquals(1, remaining.size(), "卸载 v1 后应仅剩 v2，实际剩: " + remaining.size());
        assertEquals("2.0.0", remaining.get(0).getVersion(), "剩余 bundle 应为 v2");

        List<GreetService> after = osgiLauncher.getServices(GreetService.class);
        assertEquals(1, after.size(), "卸载 v1 后服务应仅剩 v2，实际: " + after.size());
        assertEquals("v2:world", after.get(0).greet("world"), "剩余服务应返回 v2 结果");

        // 一致性：卸载 v1 后 ObjectContext 也只应查到 v2
        Collection<GreetService> afterContext = objectContext.getBeanOfTypeCollection(GreetService.class);
        assertEquals(1, afterContext.size(), "卸载 v1 后 ObjectContext 应仅剩 1 个 GreetService，实际: " + afterContext.size());
        assertEquals("v2:world", afterContext.iterator().next().greet("world"),
                "卸载 v1 后 ObjectContext 剩余服务应返回 v2 结果");

        // 6) 清理：主动基于 bundle 对象卸载 v2
        remaining.get(0).uninstall();
        assertNull(osgiLauncher.getService(GreetService.class), "全部卸载后 OSGi 服务应消失");
        assertTrue(osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                "全部卸载后 GreetService Bean 应消失");
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class),
                "全部卸载后 ObjectContext 应查不到 GreetService");
    }

    // ==================== 真实 bundle jar 生成 ====================

    /** 动态编译 bundle 实现类并打包为合法 OSGi bundle jar */
    private static File buildBundleJar(String className, String prefix, String version) throws Exception {
        Path srcFile = Paths.get("target", "osgi-bean-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "osgi-bean-classes", className);
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