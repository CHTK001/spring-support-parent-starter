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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * osgi 真实测试（框架 API 盲区补齐）：复用与 {@link OsgiBeanSwitchRealTest} 相同的
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
 * Shell 关闭、独立存储目录、导出应用接口包。</p>
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
/**
 * osgi框架apireal测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class OsgiFrameworkApiRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.api.bundle"; // bundlesymbolic名称
    private static final String GREET_SERVICE_INTERFACE = "com.chua.starter.test.osgi.GreetService"; // greet服务接口
    private static final String BUNDLE_BASE_PACKAGE = "com.chua.starter.test.osgi.bundle"; // bundlebase包

    @Autowired
    private FelixOsgiLauncher osgiLauncher; // osgilauncher

    @Autowired
    private OsgiBeanDefinitionRegister osgiBeanDefinitionRegister; // osgibeandefinition注册

    @Autowired
    private ObjectContext objectContext; // 对象上下文

    /**
     * 每个用例后清理本类安装的 bundle
    */
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

    /**
     * 真实测试：bundlelifecycle监听器 六回调随 bundle 生命周期真实触发
    */
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
 // 安装 → auto启动 → installed + 启动
            File jar = buildBundleJar("ApiGreetV1", "api", "1.0.0");
            osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
            /**
             * 断言true。
             * @param BUNDLE_SYMBOLIC_NAME) bundlesymbolic名称
             */
            assertTrue(events.contains("installed:" + BUNDLE_SYMBOLIC_NAME), "安装应触发 onBundleInstalled，实际: " + events);
            /**
             * 断言true。
             * @param BUNDLE_SYMBOLIC_NAME) bundlesymbolic名称
             */
            assertTrue(events.contains("started:" + BUNDLE_SYMBOLIC_NAME), "自动启动应触发 onBundleStarted，实际: " + events);

 // 卸载 → 停止 + uninstall → stopped + uninstalled
            osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
            /**
             * 断言true。
             * @param BUNDLE_SYMBOLIC_NAME) bundlesymbolic名称
             */
            assertTrue(events.contains("stopped:" + BUNDLE_SYMBOLIC_NAME), "卸载前停止应触发 onBundleStopped，实际: " + events);
            /**
             * 断言true。
             * @param BUNDLE_SYMBOLIC_NAME) bundlesymbolic名称
             */
            assertTrue(events.contains("uninstalled:" + BUNDLE_SYMBOLIC_NAME), "卸载应触发 onBundleUninstalled，实际: " + events);
        } finally {
            osgiLauncher.removeListener(listener);
        }
    }

    /**
     * 真实测试：felixbundle上下文 服务注册 → 可见 → 注销 → 不可见
    */
    @Test
    void bundleContextServiceRegisterUnregister() {
 // 1) 经 felixbundle上下文 注册服务
        FelixBundleContext ctx = new FelixBundleContext(osgiLauncher.getFrameworkBundleContext());
        GreetService svc = name -> "ctx:" + name;
        ctx.registerService(GreetService.class, svc);
        /**
         * 断言true。
         * @param osgiLauncher.getServices(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertTrue(osgiLauncher.getServices(GreetService.class).stream()
                        .anyMatch(s -> "ctx:world".equals(s.greet("world"))),
                "注册后 ctx 服务应出现在服务列表中");

        // 3) 注销后 ctx 服务应不在列表中
        ctx.unregisterService(GreetService.class, svc);
        /**
         * 断言false。
         * @param osgiLauncher.getServices(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertFalse(osgiLauncher.getServices(GreetService.class).stream()
                        .anyMatch(s -> "ctx:world".equals(s.greet("world"))),
                "注销后 ctx 服务应从列表中消失");
    }

    /**
     * 真实测试：felixosgibundle 服务注册 → 可见 → 注销 → 不可见
    */
    @Test
    void osgiBundleServiceRegisterUnregister() throws Exception {
        // 安装并启动 bundle，取得 bundle 实例（其上下文可用）
        File jar = buildBundleJar("ApiGreetV2", "api2", "2.0.0");
        OsgiBundle bundle = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        /**
         * 断言equals。
         * @param "ACTIVE" "活跃"
         * @param bundle.getState( bundle.获取状态(
         */
        assertEquals("ACTIVE", bundle.getState(), "bundle 应已自动启动为 ACTIVE");

        // bundle 维度注册应用侧服务
        GreetService svc = name -> "bundle-registered:" + name;
        bundle.registerService(GreetService.class, svc);
        /**
         * 断言true。
         * @param osgiLauncher.getServices(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertTrue(osgiLauncher.getServices(GreetService.class).stream()
                        .anyMatch(s -> "bundle-registered:world".equals(s.greet("world"))),
                "bundle 注册的服务应出现在服务列表中");

        // 注销后应消失
        bundle.unregisterService(GreetService.class, svc);
        /**
         * 断言false。
         * @param osgiLauncher.getServices(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertFalse(osgiLauncher.getServices(GreetService.class).stream()
                        .anyMatch(s -> "bundle-registered:world".equals(s.greet("world"))),
                "注销后 bundle 注册的服务应从列表中消失");
    }

    /**
     * 真实测试：osgibeandefinition注册 命名查询（类型:xxx）与只读语义
    */
    @Test
    void beanDefinitionNameQueryAndReadonly() throws Exception {
        // 1) 只读断言：不支持手动注册/注销
        /**
         * 断言false。
         * @param osgiBeanDefinitionRegister.isWritable( osgibeandefinitionregister.iswritable(
         */
        assertFalse(osgiBeanDefinitionRegister.isWritable(), "OSGi 注册器应为只读");
        /**
         * 断言false。
         * @param osgiBeanDefinitionRegister.isSupport(null osgibeandefinitionregister.issupport(空
         */
        assertFalse(osgiBeanDefinitionRegister.isSupport(null), "OSGi 注册器不应支持手动 BeanDefinition");
        assertThrows(UnsupportedOperationException.class, () -> osgiBeanDefinitionRegister.register(null),
                "手动注册应抛 UnsupportedOperationException");
        assertThrows(UnsupportedOperationException.class, () -> osgiBeanDefinitionRegister.unregister("any"),
                "手动注销应抛 UnsupportedOperationException");

 // 2) 安装 bundle 注册服务后，命名查询 类型:xxx 应命中
        File jar = buildBundleJar("ApiGreetV3", "api3", "3.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        Collection<BeanDefinition> defs = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        /**
         * 断言equals。
         * @param 1 1
         * @param defs.size( defs.大小(
         */
        assertEquals(1, defs.size(), "应查询到 1 个 GreetService Bean");
        String name = defs.iterator().next().getName();
        BeanDefinition byName = osgiBeanDefinitionRegister.getBeanDefinition(name);
        /**
         * 断言not空。
         * @param byName by名称
         * @param BeanDefinition" Beandefinition"
         */
        assertNotNull(byName, "按名称 " + name + " 应能查到 BeanDefinition");
        /**
         * 断言equals。
         * @param 类型应匹配" greet服务接口
         * @param byName.getType( by名称.获取类型(
         */
        assertEquals(GREET_SERVICE_INTERFACE, byName.getType(), "BeanDefinition 类型应匹配");
        /**
         * 断言true。
         * @param osgiBeanDefinitionRegister.containsBean(name osgibeandefinitionregister.containsbean(名称
         */
        assertTrue(osgiBeanDefinitionRegister.containsBean(name), "containsBean 应为 true");

        // 3) 卸载后命名查询应为空
        osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .forEach(OsgiBundle::uninstall);
        /**
         * 断言空。
         * @param osgiBeanDefinitionRegister.getBeanDefinition(name osgibeandefinitionregister.获取beandefinition(名称
         */
        assertNull(osgiBeanDefinitionRegister.getBeanDefinition(name), "卸载后命名查询应为 null");
    }

    /**
     * 真实测试：osgibeandefinition注册 关闭 后退避（新实例，不影响共享注册器）
    */
    @Test
    void beanDefinitionCloseBackoff() throws Exception {
        OsgiBeanDefinitionRegister isolated = new OsgiBeanDefinitionRegister();
        isolated.setOsgiLauncher(osgiLauncher);
        isolated.initialize();
        /**
         * 断言false。
         * @param isolated.isClosed( isolated.是否关闭(
         */
        assertFalse(isolated.isClosed(), "初始化后不应关闭");

        File jar = buildBundleJar("ApiGreetV4", "api4", "4.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        /**
         * 断言equals。
         * @param 1 1
         * @param isolated.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE isolated.获取beandefinition的类型(greet服务接口
         */
        assertEquals(1, isolated.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).size(),
                "关闭前应能查询到 OSGi Bean");

        isolated.close();
        /**
         * 断言true。
         * @param isolated.isClosed( isolated.是否关闭(
         */
        assertTrue(isolated.isClosed(), "close 后应处于关闭状态");
        /**
         * 断言true。
         * @param isolated.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE isolated.获取beandefinition的类型(greet服务接口
         */
        assertTrue(isolated.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE).isEmpty(),
                "close 后查询应返回空");
        /**
         * 断言false。
         * @param ":1" ":1"
         */
        assertFalse(isolated.containsBean(GREET_SERVICE_INTERFACE + ":1"), "close 后 containsBean 应为 false");
    }

    /**
     * 真实测试：bundle状态查询 框架状态查询
    */
    @Test
    void frameworkStateQuery() throws Exception {
        long before = osgiLauncher.getBundleCount();
        /**
         * 断言true。
         * @param before 之前
         */
        assertTrue(before > 0, "系统 bundle 存在，数量应 > 0，实际: " + before);

        File jar = buildBundleJar("ApiGreetV5", "api5", "5.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());

 // 获取bundle(symbolic名称)
        OsgiBundle byName = osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME);
        /**
         * 断言not空。
         * @param byName by名称
         * @param 应命中" 应命中"
         */
        assertNotNull(byName, "getBundle 应命中");

 // 获取bundlesby状态 / 获取活跃bundles
        List<OsgiBundle> active = osgiLauncher.getBundlesByState("ACTIVE");
        assertTrue(active.stream().anyMatch(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName())),
                "ACTIVE bundle 列表应包含目标 bundle");
        assertTrue(osgiLauncher.getActiveBundles().stream().anyMatch(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName())),
                "getActiveBundles 应包含目标 bundle");

 // 获取bundle数量
        /**
         * 断言equals。
         * @param 1 1
         * @param osgiLauncher.getBundleCount( osgilauncher.获取bundle数量(
         */
        assertEquals(before + 1, osgiLauncher.getBundleCount(), "安装后 bundle 总数应 +1");

 // 获取框架stats
        Map<String, Object> stats = osgiLauncher.getFrameworkStats();
        /**
         * 断言not空。
         * @param stats stats
         * @param null" 空"
         */
        assertNotNull(stats, "框架统计不应为 null");
        /**
         * 断言true。
         * @param (boolean (布尔值
         */
        assertTrue((boolean) stats.get("active"), "框架应 active");
        /**
         * 断言equals。
         * @param 1 1
         * @param ((Number ((数字
         */
        assertEquals(before + 1, ((Number) stats.get("totalBundles")).longValue(), "统计 totalBundles 应匹配");
        /**
         * 断言not空。
         * @param stats.get("bundleStates" stats.获取("bundle状态"
         */
        assertNotNull(stats.get("bundleStates"), "统计应含 bundleStates 分布");
        /**
         * 断言true。
         * @param (boolean (布尔值
         */
        assertTrue((boolean) stats.get("active"), "统计 active 标志应为 true");
    }

    /**
     * 真实测试：osgilauncherholder 全局单例生命周期
    */
    @Test
    void launcherHolderLifecycle() {
        // Spring 装配的 launcher 启动时应已注册到 Holder
        OsgiLauncher holder = OsgiLauncherHolder.getInstance();
        /**
         * 断言not空。
         * @param holder holder
         * @param 应持有启动器实例" 应持有启动器实例"
         */
        assertNotNull(holder, "Holder 应持有启动器实例");
        /**
         * 断言equals。
         * @param osgiLauncher osgilauncher
         * @param holder holder
         * @param 装配的一致" 装配的一致"
         */
        assertEquals(osgiLauncher, holder, "Holder 实例应与 Spring 装配的一致");
        /**
         * 断言true。
         * @param OsgiLauncherHolder.isActive( osgilauncherholder.是否活跃(
         */
        assertTrue(OsgiLauncherHolder.isActive(), "Holder 应报告框架激活");
    }

    /**
     * 真实测试：auto启动=false 时安装 bundle 不自动启动，需手动 启动
    */
    @Test
    void autoStartDisabledThenManualStart() throws Exception {
        osgiLauncher.setAutoStartInstalledBundles(false);
        try {
            File jar = buildBundleJar("ApiGreetV6", "api6", "6.0.0");
            OsgiBundle bundle = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
            /**
             * 断言false。
             * @param "ACTIVE".equals(bundle.getState( "活跃".equals(bundle.获取状态(
             */
            assertFalse("ACTIVE".equals(bundle.getState()), "autoStart=false 时不应自动启动，实际: " + bundle.getState());
            /**
             * 断言空。
             * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
             */
            assertNull(osgiLauncher.getService(GreetService.class), "未启动时服务不应注册");

            bundle.start();
            /**
             * 断言equals。
             * @param "ACTIVE" "活跃"
             * @param bundle.getState( bundle.获取状态(
             */
            assertEquals("ACTIVE", bundle.getState(), "手动 start 后应为 ACTIVE");
            /**
             * 断言not空。
             * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
             */
            assertNotNull(osgiLauncher.getService(GreetService.class), "手动 start 后服务应注册");
        } finally {
            osgiLauncher.setAutoStartInstalledBundles(true);
        }
    }

    /**
     * 真实测试：停止 不卸载（活跃 → RESOLVED，服务注销；再 启动 恢复）
    */
    @Test
    void stopWithoutUninstallThenRestart() throws Exception {
        File jar = buildBundleJar("ApiGreetV7", "api7", "7.0.0");
        OsgiBundle bundle = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        /**
         * 断言equals。
         * @param "ACTIVE" "活跃"
         * @param bundle.getState( bundle.获取状态(
         */
        assertEquals("ACTIVE", bundle.getState(), "安装后应为 ACTIVE");

        bundle.stop();
        /**
         * 断言equals。
         * @param "RESOLVED" "RESOLVED"
         * @param bundle.getState( bundle.获取状态(
         */
        assertEquals("RESOLVED", bundle.getState(), "stop 后应为 RESOLVED，实际: " + bundle.getState());
        /**
         * 断言空。
         * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertNull(osgiLauncher.getService(GreetService.class), "stop 后服务应注销");

        bundle.start();
        /**
         * 断言equals。
         * @param "ACTIVE" "活跃"
         * @param bundle.getState( bundle.获取状态(
         */
        assertEquals("ACTIVE", bundle.getState(), "重新 start 后应为 ACTIVE");
        /**
         * 断言not空。
         * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertNotNull(osgiLauncher.getService(GreetService.class), "重新 start 后服务应恢复");
    }

    /**
     * 真实测试：卸载不存在的 bundle 应走 warn 路径不抛异常
    */
    @Test
    void uninstallNonExistentBundle() {
        assertDoesNotThrow(() -> osgiLauncher.uninstallBundle("com.chua.test.not-exists"),
                "卸载不存在的 bundle 不应抛异常");
    }

    /**
     * 真实测试：框架未激活时 API 退避行为（独立 launcher，不影响共享实例）
    */
    @Test
    void inactiveLauncherBackoff() {
        FelixOsgiLauncher inactive = new FelixOsgiLauncher();
        /**
         * 断言false。
         * @param inactive.isActive( inactive.是否活跃(
         */
        assertFalse(inactive.isActive(), "未启动的 launcher 应非激活");
        /**
         * 断言true。
         * @param inactive.getServices(GreetService.class inactive.获取服务(greet服务.类
         */
        assertTrue(inactive.getServices(GreetService.class).isEmpty(), "未激活时 getServices 应返回空");
        /**
         * 断言true。
         * @param inactive.getBundles( inactive.获取bundles(
         */
        assertTrue(inactive.getBundles().isEmpty(), "未激活时 getBundles 应返回空");
        /**
         * 断言空。
         * @param inactive.getService(GreetService.class inactive.获取服务(greet服务.类
         */
        assertNull(inactive.getService(GreetService.class), "未激活时 getService 应返回 null");
        assertThrows(IllegalStateException.class, () -> inactive.installBundle("file:any.jar"),
                "未激活时 installBundle 应抛 IllegalStateException");
        assertDoesNotThrow(() -> inactive.uninstallBundle("any"), "未激活时 uninstallBundle 不应抛异常");
    }

    /** 真实测试：ObjectContext 与 osgiLauncher 全流程结果一致（复用注入的 ObjectContext） */    @Test
    /**
     * 对象上下文AndLauncherConsistent。
     *
     * @throws Exception 当执行过程不满足前置条件时
     */
    void objectContextAndLauncherConsistent() throws Exception {
        // 初始一致
        /**
         * 断言空。
         * @param objectContext.getBeanOfTypeSafely(GreetService.class 对象上下文.获取Bean的类型safely(greetservice.类
         */
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "初始 ObjectContext 应查不到");
        /**
         * 断言空。
         * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertNull(osgiLauncher.getService(GreetService.class), "初始 osgiLauncher 应查不到");

        File jar = buildBundleJar("ApiGreetV8", "api8", "8.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        GreetService fromLauncher = osgiLauncher.getService(GreetService.class);
        GreetService fromContext = objectContext.getBeanOfTypeSafely(GreetService.class);
        /**
         * 断言not空。
         * @param fromLauncher 从launcher
         * @param 应查到" 应查到"
         */
        assertNotNull(fromLauncher, "osgiLauncher 应查到");
        /**
         * 断言not空。
         * @param fromContext 从上下文
         * @param 应查到" 应查到"
         */
        assertNotNull(fromContext, "ObjectContext 应查到");
        /**
         * 断言equals。
         * @param fromLauncher.greet("world" 从launcher.greet("world"
         */
        assertEquals(fromLauncher.greet("world"), fromContext.greet("world"),
                "ObjectContext 与 osgiLauncher 查询结果应一致");

 // 按名查询桥接：经 注册 查到 Beandefinition 的完整名称，再由 对象上下文 按名获取
        Collection<BeanDefinition> defs = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        /**
         * 断言equals。
         * @param 1 1
         * @param defs.size( defs.大小(
         */
        assertEquals(1, defs.size(), "应查询到 1 个 GreetService Bean");
        String beanName = defs.iterator().next().getName();
        GreetService byName = objectContext.getBean(beanName, GreetService.class);
        /**
         * 断言not空。
         * @param byName by名称
         * @param " "
         */
        assertNotNull(byName, "ObjectContext 按名 (" + beanName + ") 应查到 GreetService");
        /**
         * 断言equals。
         * @param "api8:world" "api8:world"
         * @param byName.greet("world" by名称.greet("world"
         */
        assertEquals("api8:world", byName.greet("world"), "按名查询结果应与服务一致");

        osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .forEach(OsgiBundle::uninstall);
        /**
         * 断言空。
         * @param objectContext.getBeanOfTypeSafely(GreetService.class 对象上下文.获取Bean的类型safely(greetservice.类
         */
        assertNull(objectContext.getBeanOfTypeSafely(GreetService.class), "卸载后 ObjectContext 应查不到");
        /**
         * 断言空。
         * @param osgiLauncher.getService(GreetService.class osgilauncher.获取服务(greet服务.类
         */
        assertNull(osgiLauncher.getService(GreetService.class), "卸载后 osgiLauncher 应查不到");
    }

    /**
     * 真实测试：框架停止链路 —— 是否活跃=false + Holder 清理（独立 launcher，不影响共享实例）
    */
    @Test
    void frameworkStopChain() throws Exception {
        StopChainBundleApplication.STARTED = false;
        StopChainBundleApplication.STOPPED = false;
        // 编程式注册 SPI 应用（静态类，可被 SPI 实例化），随后启动独立 launcher 验证框架生命周期
        com.chua.common.support.spi.ServiceProvider.of(com.chua.common.support.osgi.BundleApplication.class)
                .register("test-stop-app", StopChainBundleApplication.class);
        try {
            FelixOsgiLauncher isolated = new FelixOsgiLauncher();
            isolated.start(java.util.Map.of(
                    "org.osgi.framework.storage", "target/osgi-cache-stop-test",
                    "org.osgi.framework.storage.clean", "onFirstInit"));
            /**
             * 断言true。
             * @param isolated.isActive( isolated.是否活跃(
             */
            assertTrue(isolated.isActive(), "独立框架应激活");
            /**
             * 断言equals。
             * @param isolated isolated
             * @param com.chua.common.support.osgi.OsgiLauncherHolder.getInstance( com.chua.通用.支持.osgi.osgilauncherholder.获取instance(
             */
            assertEquals(isolated, com.chua.common.support.osgi.OsgiLauncherHolder.getInstance(),
                    "框架启动后 Holder 应指向该 launcher");

 // SPI 注册的 bundleapplication 应可被 SPI 发现（collect 能实例化）
            assertEquals(1, com.chua.common.support.spi.ServiceProvider
                            .of(com.chua.common.support.osgi.BundleApplication.class).collect().size(),
                    "SPI 应能发现注册的 BundleApplication");

            isolated.stop();
            /**
             * 断言false。
             * @param isolated.isActive( isolated.是否活跃(
             */
            assertFalse(isolated.isActive(), "框架停止后应非激活");
            /**
             * 断言空。
             * @param com.chua.common.support.osgi.OsgiLauncherHolder.getInstance( com.chua.通用.支持.osgi.osgilauncherholder.获取instance(
             */
            assertNull(com.chua.common.support.osgi.OsgiLauncherHolder.getInstance(),
                    "框架停止后 Holder 应被清理为 null");
        } finally {
            // 恢复 Holder 为共享实例，避免污染其他用例
            if (com.chua.common.support.osgi.OsgiLauncherHolder.getInstance() == null
                    || !com.chua.common.support.osgi.OsgiLauncherHolder.getInstance().isActive()) {
                com.chua.common.support.osgi.OsgiLauncherHolder.setInstance(osgiLauncher);
            }
        }
    }

    /**
     * bundleapplication SPI 测试辅助：静态类（可被 SPI 新instance），静态标志捕获回调
    */
    public static class StopChainBundleApplication implements com.chua.common.support.osgi.BundleApplication {

        /**
         * 是否已回调 onbundle启动
        */
        public static volatile boolean STARTED;
        /**
         * 是否已回调 onbundle停止
        */
        public static volatile boolean STOPPED;

        @Override
        public void onBundleStart(com.chua.common.support.osgi.BundleContext context) {
            STARTED = true;
        }

        @Override
        public void onBundleStop(com.chua.common.support.osgi.BundleContext context) {
            STOPPED = true;
        }
    }

    /**
     * 真实测试：重复安装同一 位置 should be 幂等 — 返回同一 bundle，总数不变（osgi 规范）
    */
    @Test
    void reinstallSameVersionBundle() throws Exception {
        File jar = buildBundleJar("ApiGreetV9", "api9", "9.0.0");
        OsgiBundle first = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        long afterFirst = osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .count();

 // 重复安装相同 位置：osgi 规范本次幂等，返回同一 bundle，不新增副本
        OsgiBundle second = osgiLauncher.installBundle("file:" + jar.getAbsolutePath());
        /**
         * 断言equals。
         * @param first.getVersion( 第一个.获取版本(
         */
        assertEquals(first.getVersion(), second.getVersion(), "重复安装应返回同版本 bundle");
        /**
         * 断言equals。
         * @param second.getState( second.获取状态(
         */
        assertEquals(second.getState(), first.getState(), "重复安装返回的 bundle 状态应一致");
        /**
         * 断言equals。
         * @param afterFirst 之后第一个
         * @param osgiLauncher.getBundles( osgilauncher.获取bundles(
         */
        assertEquals(afterFirst, osgiLauncher.getBundles().stream()
                        .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                        .count(),
                "相同 location 重复安装不应新增 bundle");

        // 卸载清理
        osgiLauncher.getBundles().stream()
                .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                .forEach(OsgiBundle::uninstall);
        /**
         * 断言equals。
         * @param 0 0
         * @param osgiLauncher.getBundles( osgilauncher.获取bundles(
         */
        assertEquals(0, osgiLauncher.getBundles().stream()
                        .filter(b -> BUNDLE_SYMBOLIC_NAME.equals(b.getSymbolicName()))
                        .count(),
                "清理后应无同名 bundle");
    }

    /**
     * 真实测试：osgibeandefinition注册 双参 获取Beandefinition的类型(名称, 类型名称) 重载
    */
    @Test
    void beanDefinitionByNameAndType() throws Exception {
        File jar = buildBundleJar("ApiGreetV10", "api10", "10.0.0");
        osgiLauncher.installBundle("file:" + jar.getAbsolutePath());

 // 名称 匹配路径：用 获取Beandefinition的类型 拿到的名字反查
        Collection<BeanDefinition> defs = osgiBeanDefinitionRegister.getBeanDefinitionOfType(GREET_SERVICE_INTERFACE);
        /**
         * 断言equals。
         * @param 1 1
         * @param defs.size( defs.大小(
         */
        assertEquals(1, defs.size(), "应查询到 1 个 GreetService Bean");
        String name = defs.iterator().next().getName();
        Collection<BeanDefinition> byNameAndType =
                osgiBeanDefinitionRegister.getBeanDefinitionOfType(name, GREET_SERVICE_INTERFACE);
        /**
         * 断言equals。
         * @param 1 1
         * @param byNameAndType.size( by名称andtype.大小(
         */
        assertEquals(1, byNameAndType.size(), "双参查询（name 匹配）应命中 1 个");
        /**
         * 断言equals。
         * @param name 名称
         * @param byNameAndType.iterator( by名称andtype.迭代器(
         */
        assertEquals(name, byNameAndType.iterator().next().getName(), "双参查询返回的 BeanDefinition 名称应一致");

 // 名称 不匹配路径：返回空
        /**
         * 断言true。
         * @param GREET_SERVICE_INTERFACE).isEmpty() greet服务接口
         */
        assertTrue(osgiBeanDefinitionRegister.getBeanDefinitionOfType("not-exists", GREET_SERVICE_INTERFACE).isEmpty(),
                "name 不匹配时双参查询应返回空");
    }

    // ==================== 真实 bundle jar 生成 ====================

    /**
     * 动态编译 bundle 实现类并打包为合法 osgi bundle jar
     *
     * @param className 类名称
     * @param prefix 前缀
     * @param version 版本
     * @return 构建bundlejar的结果
     */
    private static File buildBundleJar(String className, String prefix, String version) throws Exception {
        Path srcFile = Paths.get("target", "osgi-api-src", className + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, bundleSource(className, prefix), StandardCharsets.UTF_8);

        Path classOut = Paths.get("target", "osgi-api-classes", className);
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

        /**
         * 尝试。
         * @param FileOutputStream(jarFile 文件输出流(jar文件
         */
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
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
        return jarFile;
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