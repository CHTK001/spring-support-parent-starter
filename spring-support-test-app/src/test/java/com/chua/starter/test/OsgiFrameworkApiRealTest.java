package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.definition.BeanDefinition;
import com.chua.common.support.osgi.BundleLifecycleListener;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.osgi.OsgiLauncherHolder;
import com.chua.osgi.support.FelixBundleContext;
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OSGi 真实测试（框架 API 盲区补齐）：复用与 {@link OsgiBeanSwitchRealTest} 相同的
 * Spring 上下文（Spring 测试缓存复用，不重复启动 Felix），对以下能力做真实验证：
 * <ul>
 *   <li>{@link BundleLifecycleListener} 生命周期事件（安装/启动/停止/卸载回调）</li>
 *   <li>{@link FelixBundleContext} 服务注册/注销/查询</li>
 *   <li>{@link OsgiBundle}（FelixOsgiBundle）服务注册/注销/查询</li>
 *   <li>{@link OsgiBeanDefinitionRegister} 命名查询 / close 后退避 / 只读断言</li>
 *   <li>框架状态查询（getBundle/getBundlesByState/getActiveBundles/getBundleCount/getFrameworkStats）</li>
 *   <li>{@link OsgiLauncherHolder} 全局单例生命周期</li>
 *   <li>autoStart 关闭、stop 不卸载、不存在 bundle 卸载、框架未激活 API</li>
 * </ul>
 *
 * <p>与 {@link OsgiBeanSwitchRealTest} 相同的 context 配置：
 * shell 关闭、独立存储目录、导出应用接口包。</p>
 *
 * @author CH
 * @since 2026/09/11
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-api-test",
        "plugin.osgi.shell.enabled=false",
        "plugin.osgi.framework-properties[org.osgi.framework.system.packages.extra]=com.chua.starter.test.osgi"
})
class OsgiFrameworkApiRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.api.bundle";
    private static final String GREET_SERVICE_INTERFACE = "com.chua.starter.test.osgi.GreetService";
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle";

    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    @Autowired
    private OsgiBeanDefinitionRegister osgiBeanDefinitionRegister;

    @Autowired
    private ObjectContext objectContext;

    /** 每个用例后清理本类安装的 bundle */
    @AfterEach
    void cleanup() {
        try {
            osgiLauncher.getBundles().stream()
                    .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                    .forEach(OsgiBundle::uninstall);
        } catch (Exception ignored) {
        }
        try {
            osgiLauncher.setAutoStartInstalledBundles(true);
        } catch (Exception ignored) {
        }
    }

    /** 真实测试：BundleLifecycleListener 六回调随 bundle 生命周期真实触发 */
    @Test
    void lifecycleListenerEventsFire() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        BundleLifecycleListener listener = new BundleLifecycleListener() {
            @Override public void onBundleInstalled(String n) { events.add("installed:" + n); }
            @Override public void onBundleStarted(String n) { events.add("started:" + n); }
            @Override public void onBundleStopped(String n) { events.add("stopped:" + n); }
            @Override public void onBundleUpdated(String n, String v) { events.add("updated:" + n + ":" + v); }
            @Override public void onBundleUninstalled(String n) { events.add("uninstalled:" + n); }
            @Override public void onBundleStateChanged(String n, String o, String v) { events.add("state:" + n); }
        };
        osgiLauncher.addListener(listener);
        try {
            // 安装 → autoStart → installed + started
            File jar = buildBundleJar("ApiGreetV1", "api", "1.0.0");
            osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
            assertTrue(events.contains("installed:" + BUNDLE_SYMBOLIC_NAME), "安装应触发 onBundleInstalled，实际: " + events);
            assertTrue(events.contains("started:" + BUNDLE_SYMBOLIC_NAME), "自动启动应触发 onBundleStarted，实际: " + events);

            // 卸载 → stop + uninstall → stopped + uninstalled
            osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
            assertTrue(events.contains("stopped:" + BUNDLE_SYMBOLIC_NAME), "卸载前停止应触发 onBundleStopped，实际: " + events);
            assertTrue(events.contains("uninstalled:" + BUNDLE_SYMBOLIC_NAME), "卸载应触发 onBundleUninstalled，实际: " + events);
        } finally {
            osgiLauncher.removeListener(listener);
        }
    }

    /** 真实测试：FelixBundleContext 服务注册 → 可见 → 注销 → 不可见 */
    @Test
    void bundleContextServiceRegisterUnregister() {
        // 1) 注册前不可见
        assertNull(osgiLauncher.getService(GreetService.class), "注册前不应可见");

        // 2) 经 FelixBundleContext 注册服务
        FelixBundleContext ctx = new FelixBundleContext(osgiLauncher.getFrameworkBundleContext());
        GreetService svc = name -> "ctx:" + name;
        ctx.registerService(GreetService.class, svc);
        GreetService found = osgiLauncher.getService(GreetService.class);
        assertNotNull(found, "注册后应可见");
        assertEquals("ctx:world", found.greet("world"), "服务内容应匹配");

        // 3) 注销后不可见
        ctx.unregisterService(GreetService.class, svc);
        assertNull(osgiLauncher.getService(GreetService.class), "注销后应不可见");
    }

    /** 真实测试：FelixOsgiBundle 服务注册 → 可见 → 注销 → 不可见 */
    @Test
    void osgiBundleServiceRegisterUnregister() throws Exception {
        // 安装并启动 bundle，取得 bundle 实例（其上下文可用）
        File jar = buildBundleJar("ApiGreetV2", "api2", "2.0.0");
        OsgiBundle bundle = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        assertEquals("ACTIVE", bundle.getState(), "bundle 应已自动启动为 ACTIVE");

        // bundle 维度注册应用侧服务
        GreetService svc = name -> "bundle:" + name;
        bundle.registerService(GreetService.class, svc);
        GreetService found = osgiLauncher.getService(GreetService.class);
        assertNotNull(found, "bundle 注册后应可见");
        assertEquals("bundle:world", found.greet("world"), "bundle 注册服务内容应匹配");

        // 注销后不可见（Activator 注册的 api2 服务仍在，此处注销的是 bundle 注册的服务）
        bundle.unregisterService(GreetService.class, svc);
        List<GreetService> after = osgiLauncher.getServices(GreetService.class);
        assertFalse(after.stream().anyMatch(s -> "bundle:world".equals(s.greet("world"))),
                "注销后 bundle 注册的服务应不可见，实际: " + after);
    }

    /** 真实测试：OsgiBeanDefinitionRegister 命名查询（type:xxx）与只读语义 */
    @Test
    void beanDefinitionNameQueryAndReadonly() throws Exception {
        // 1) 只读断言：不支持手动注册/注销
        assertFalse(osgiBeanDefinitionRegister.isWritable(), "OSGi 注册器应为只读");
        assertFalse(osgiBeanDefinitionRegister.isSupport(null), "OSGi 注册器不应支持手动 BeanDefinition");
        assertThrows(UnsupportedOperationException.class, () -> osgiBeanDefinitionRegister.register(null),
                "手动注册应抛 UnsupportedOperationException");
        assertThrows(UnsupportedOperationException.class, () -> osgiBeanDefinitionRegister.unregister("any"),
                "手动注销应抛 UnsupportedOperationException");

        // 2) 安装 bundle 注册服务后，命名查询 type:xxx 应命中
        File jar = buildBundleJar("ApiGreetV3", "api3", "3.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        Collection<BeanDefinition> defs = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        assertEquals(1, defs.size(), "应查询到 1 个 GreetService Bean");
        String name = defs.iterator().next().getName();
        BeanDefinition byName = osgiBeanDefinitionRegister.getBeanDefinition(name);
        assertNotNull(byName, "按名称 " + name + " 应能查到 BeanDefinition");
        assertEquals(GREET_SERVICE_INTERFACE, byName.getType(), "BeanDefinition 类型应匹配");
        assertTrue(osgiBeanDefinitionRegister.containsBean(name), "containsBean 应为 true");

        // 3) 卸载后命名查询应为空
        osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .forEach(OsgiBundle::uninstall);
        assertNull(osgiBeanDefinitionRegister.getBeanDefinition(name), "卸载后命名查询应为 null");
    }

    /** 真实测试：OsgiBeanDefinitionRegister close 后退避（新实例，不影响共享注册器） */
    @Test
    void beanDefinitionCloseBackoff() throws Exception {
        OsgiBeanDefinitionRegister isolated = new OsgiBeanDefinitionRegister();
        isolated.setOsgiLauncher(osgiLauncher);
        isolated.initialize();
        assertFalse(isolated.isClosed(), "初始化后不应关闭");

        File jar = buildBundleJar("ApiGreetV4", "api4", "4.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        assertEquals(1, isolated.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).size(),
                "关闭前应能查询到 OSGi Bean");

        isolated.close();
        assertTrue(isolated.isClosed(), "close 后应处于关闭状态");
        assertTrue(isolated.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                "close 后查询应返回空");
        assertFalse(isolated.containsBean(GREET_SERVICE_INTERFACE + ":1"), "close 后 containsBean 应为 false");
    }

    /** 真实测试：BundleStateQuery 框架状态查询 */
    @Test
    void frameworkStateQuery() throws Exception {
        long before = osgiLauncher.getBundleCount();
        assertTrue(before > 0, "系统 bundle 存在，数量应 > 0，实际: " + before);

        File jar = buildBundleJar("ApiGreetV5", "api5", "5.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());

        // getBundle(symbolicName)
        OsgiBundle byName = osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME);
        assertNotNull(byName, "getBundle 应命中");

        // getBundlesByState / getActiveBundles
        List<OsgiBundle> active = osgiLauncher.getBundlesByState("ACTIVE");
        assertTrue(active.stream().anyMatch(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName())),
                "ACTIVE bundle 列表应包含目标 bundle");
        assertTrue(osgiLauncher.getActiveBundles().stream().anyMatch(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName())),
                "getActiveBundles 应包含目标 bundle");

        // getBundleCount
        assertEquals(before + 1, osgiLauncher.getBundleCount(), "安装后 bundle 总数应 +1");

        // getFrameworkStats
        Map<String, Object> stats = osgiLauncher.getFrameworkStats();
        assertNotNull(stats, "框架统计不应为 null");
        assertTrue((boolean) stats.get("active"), "框架应 active");
        assertEquals(before + 1, ((Number) stats.get("totalBundles")).longValue(), "统计 totalBundles 应匹配");
        assertNotNull(stats.get("bundleStates"), "统计应含 bundleStates 分布");
        assertTrue((boolean) stats.get("active"), "统计 active 标志应为 true");
    }

    /** 真实测试：OsgiLauncherHolder 全局单例生命周期 */
    @Test
    void launcherHolderLifecycle() {
        // Spring 装配的 launcher 启动时应已注册到 Holder
        OsgiLauncher holder = OsgiLauncherHolder.getInstance();
        assertNotNull(holder, "Holder 应持有启动器实例");
        assertEquals(osgiLauncher, holder, "Holder 实例应与 Spring 装配的一致");
        assertTrue(OsgiLauncherHolder.isActive(), "Holder 应报告框架激活");
    }

    /** 真实测试：autoStart=false 时安装 bundle 不自动启动，需手动 start */
    @Test
    void autoStartDisabledThenManualStart() throws Exception {
        osgiLauncher.setAutoStartInstalledBundles(false);
        try {
            File jar = buildBundleJar("ApiGreetV6", "api6", "6.0.0");
            OsgiBundle bundle = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
            assertFalse("ACTIVE".equals(bundle.getState()), "autoStart=false 时不应自动启动，实际: " + bundle.getState());
            assertNull(osgiLauncher.getService(GreetService.class), "未启动时服务不应注册");

            bundle.start();
            assertEquals("ACTIVE", bundle.getState(), "手动 start 后应为 ACTIVE");
            assertNotNull(osgiLauncher.getService(GreetService.class), "手动 start 后服务应注册");
        } finally {
            osgiLauncher.setAutoStartInstalledBundles(true);
        }
    }

    /** 真实测试：stop 不卸载（ACTIVE → RESOLVED，服务注销；再 start 恢复） */
    @Test
    void stopWithoutUninstallThenRestart() throws Exception {
        File jar = buildBundleJar("ApiGreetV7", "api7", "7.0.0");
        OsgiBundle bundle = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        assertEquals("ACTIVE", bundle.getState(), "安装后应为 ACTIVE");

        bundle.stop();
        assertEquals("RESOLVED", bundle.getState(), "stop 后应为 RESOLVED，实际: " + bundle.getState());
        assertNull(osgiLauncher.getService(GreetService.class), "stop 后服务应注销");

        bundle.start();
        assertEquals("ACTIVE", bundle.getState(), "重新 start 后应为 ACTIVE");
        assertNotNull(osgiLauncher.getService(GreetService.class), "重新 start 后服务应恢复");
    }

    /** 真实测试：卸载不存在的 bundle 应走 warn 路径不抛异常 */
    @Test
    void uninstallNonExistentBundle() {
        assertDoesNotThrow(() -> osgiLauncher.uninstallBundle("com.chua.test.not-exists"),
                "卸载不存在的 bundle 不应抛异常");
    }

    /** 真实测试：框架未激活时 API 退避行为（独立 launcher，不影响共享实例） */
    @Test
    void inactiveLauncherBackoff() {
        FelixOsgiLauncher inactive = new FelixOsgiLauncher();
        assertFalse(inactive.isActive(), "未启动的 launcher 应非激活");
        assertTrue(inactive.getServices(GreetService.class).isEmpty(), "未激活时 getServices 应返回空");
        assertTrue(inactive.getBundles().isEmpty(), "未激活时 getBundles 应返回空");
        assertNull(inactive.getService(GreetService.class), "未激活时 getService 应返回 null");
        assertThrows(IllegalStateException.class, () -> inactive.installBundle("file:any.jar"),
                "未激活时 installBundle 应抛 IllegalStateException");
        assertDoesNotThrow(() -> inactive.uninstallBundle("any"), "未激活时 uninstallBundle 不应抛异常");
    }

    /** 真实测试：ObjectContext 与 osgiLauncher 全流程结果一致（复用注入的 ObjectContext） */
    @Test
    void objectContextAndLauncherConsistent() throws Exception {
        // 初始一致
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "初始 ObjectContext 应查不到");
        assertNull(osgiLauncher.getService(GreetService.class), "初始 osgiLauncher 应查不到");

        File jar = buildBundleJar("ApiGreetV8", "api8", "8.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        GreetService fromLauncher = osgiLauncher.getService(GreetService.class);
        GreetService fromContext = objectContext.getBeanOfTypeSafely(GreetService.class);
        assertNotNull(fromLauncher, "osgiLauncher 应查到");
        assertNotNull(fromContext, "ObjectContext 应查到");
        assertEquals(fromLauncher.greet("world"), fromContext.greet("world"),
                "ObjectContext 与 osgiLauncher 查询结果应一致");

        osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .forEach(OsgiBundle::uninstall);
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "卸载后 ObjectContext 应查不到");
        assertNull(osgiLauncher.getService(GreetService.class), "卸载后 osgiLauncher 应查不到");
    }

    // ==================== 真实 bundle jar 生成 ====================

    /** 动态编译 bundle 实现类并打包为合法 OSGi bundle jar */
    private static File buildBundleJar(String className, String prefix, String version) throws Exception {
        Path srcFile = Paths.get("target", "osgi-api-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "osgi-api-classes", className);
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

        File jarFile = new File("target/" + className + "-api-bundle.jar");
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