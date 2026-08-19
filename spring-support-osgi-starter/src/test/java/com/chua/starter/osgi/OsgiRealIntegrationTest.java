package com.chua.starter.osgi;

import com.chua.common.support.objects.DefaultObjectContext;
import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.ObjectContextConfig;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.osgi.properties.OsgiProperties;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * spring-support-osgi-starter 真实集成测试入口。
 *
 * <p>通过 {@code main} 方法覆盖真实 Felix 框架启动 + ObjectContext 桥接 + Gogo shell bundle 安装的完整链路：</p>
 * <ul>
 *   <li>FelixOsgiLauncher.start() 真实启动 Felix 框架</li>
 *   <li>FelixOsgiLauncher.installBundle() 真实安装 gogo.runtime / gogo.command / gogo.shell bundle</li>
 *   <li>Felix 框架内置 telnet (gogo.shell) 端口绑定</li>
 *   <li>OsgiBeanDefinitionRegister.setOsgiLauncher() 注入后查询 OSGi 服务</li>
 *   <li>ObjectContext.registerBean(BeanDefinitionRegister) 编程式挂接到核心容器</li>
 *   <li>通过 ObjectContext.getBeanOfType 跨容器获取 OSGi 注册的服务</li>
 *   <li>FelixOsgiLauncher.stop() 关闭后所有查询立即失效</li>
 * </ul>
 *
 * <p>运行方式：</p>
 * <pre>
 * cd spring-support-parent-starter/spring-support-osgi-starter
 * java -cp target/test-classes;... com.chua.starter.osgi.OsgiRealIntegrationTest
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class OsgiRealIntegrationTest {

    /**
     * 测试服务接口（顶层定义，便于 Class.forName 反射查找）。
     */
    public interface IntegrationGreeting {
        String greet(String name);
    }

    /**
     * 测试入口。
     *
     * @param args 命令行参数（忽略）
     */
    public static void main(String[] args) {
        Result r = new Result("OsgiRealIntegration");
        final FelixOsgiLauncher[] launcherHolder = new FelixOsgiLauncher[1];
        try {
            FelixOsgiLauncher launcher = new FelixOsgiLauncher();
            launcherHolder[0] = launcher;
            Map<String, String> config = new HashMap<>();
            config.put("org.osgi.framework.storage",
                    new File("./target/osgi-cache").getAbsolutePath());
            config.put("org.osgi.framework.startlevel.beginning", "4");

            r.run("felixStart", () -> t_felixStart(launcherHolder[0], config));
            r.run("gogoRuntimeInstalled", () -> t_gogoBundleInstalled(launcherHolder[0],
                    "org.apache.felix.gogo.runtime", "org.apache.felix.gogo.runtime-1.1.6.jar"));
            r.run("gogoCommandInstalled", () -> t_gogoBundleInstalled(launcherHolder[0],
                    "org.apache.felix.gogo.command", "org.apache.felix.gogo.command-1.1.2.jar"));
            r.run("gogoShellInstalled", () -> t_gogoBundleInstalled(launcherHolder[0],
                    "org.apache.felix.gogo.shell", "org.apache.felix.gogo.shell-1.1.4.jar"));
            r.run("osgiRegisterDelegateLookup", () -> t_osgiRegisterDelegateLookup(launcherHolder[0]));
            r.run("objectContextBridgesOsgiService", () -> t_objectContextBridgesOsgiService(launcherHolder[0]));
            r.run("objectContextMultipleBridgesSameType", () -> t_objectContextMultipleBridgesSameType(launcherHolder[0]));
            r.run("inactiveLauncherReturnsEmpty", () -> t_inactiveLauncherReturnsEmpty(launcherHolder[0]));
            r.run("osgiPropertiesDefaultValues", OsgiRealIntegrationTest::t_osgiPropertiesDefaultValues);
        } catch (Throwable t) {
            r.failures.add("bootstrap -> " + t.getMessage());
            System.out.println("  [FAIL] bootstrap -> " + t.getMessage());
        } finally {
            r.run("felixStop", () -> t_felixStop(launcherHolder[0]));
        }
        r.summary();
        if (!r.failures.isEmpty()) {
            System.err.println("FAILURES:");
            for (String f : r.failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // ==================== 测试用例 ====================

    private static void t_felixStart(FelixOsgiLauncher launcher, Map<String, String> config) {
        launcher.start(config);
        assertTrue(launcher.isActive(), "Felix 框架应处于激活状态");
        assertTrue(!launcher.getBundles().isEmpty(), "应至少包含 system bundle");
        System.out.println("[Real] Felix 已激活，bundles: " + launcher.getBundles().size());
    }

    /**
     * 真实安装一个 bundle（FelixOsgiLauncher.installBundle 路径）。
     */
    private static void t_gogoBundleInstalled(FelixOsgiLauncher launcher, String symbolicName, String jarName) {
        URL jarUrl = locateJar(jarName);
        assertNotNull(jarUrl, "未在 classpath 中找到 " + jarName);
        launcher.installBundle(toExternalForm(jarUrl));
        assertTrue(isBundleInstalled(launcher, symbolicName),
                symbolicName + " bundle 应已安装");
        System.out.println("[Real] " + symbolicName + " 已安装");
    }

    private static void t_osgiRegisterDelegateLookup(FelixOsgiLauncher launcher) {
        // 通过 launcher 反射调用 BundleContext.registerService 注册一个 OSGi 服务
        Object bundleContext = invokeGetBundleContext(launcher);
        assertNotNull(bundleContext, "应能获取 Felix BundleContext");

        IntegrationGreetingImpl impl = new IntegrationGreetingImpl();
        invokeBundleContextRegisterService(bundleContext,
                IntegrationGreeting.class.getName(), impl);

        try {
            // Felix OSGi 服务注册是异步的，给一点时间让事件分发完成
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        IntegrationGreeting retrieved = launcher.getService(IntegrationGreeting.class);
        assertNotNull(retrieved, "OSGi 注册的服务应能被 launcher.getService 查到");
        assertEquals("Hello, chua (via OSGi)", retrieved.greet("chua"),
                "返回值应来自 OSGi 服务实例");
        System.out.println("[Real] OSGi 服务查询成功: " + retrieved.greet("chua"));
    }

    private static void t_objectContextBridgesOsgiService(FelixOsgiLauncher launcher) {
        ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());

        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();
        boolean added = ctx.registerBean(register);
        assertTrue(added, "首次注册 OsgiBeanDefinitionRegister 应返回 true");

        // 之前测试已注册 IntegrationGreeting，ObjectContext 应能查到
        IntegrationGreeting greeting = ctx.getBeanOfType(IntegrationGreeting.class);
        assertNotNull(greeting, "ObjectContext 应能通过 OsgiBeanDefinitionRegister 桥接查到 OSGi 服务");
        assertEquals("Hello, bridged (via OSGi)", greeting.greet("bridged"),
                "返回值应来自 OSGi 服务实例");
        System.out.println("[Real] ObjectContext 桥接 OSGi 成功: " + greeting.greet("bridged"));
    }

    private static void t_objectContextMultipleBridgesSameType(FelixOsgiLauncher launcher) {
        ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();

        boolean first = ctx.registerBean(register);
        boolean second = ctx.registerBean(register); // 重复注册同名应被忽略
        assertTrue(first, "首次注册应成功");
        assertFalse(second, "重复注册同名 Register 应被忽略");
    }

    private static void t_inactiveLauncherReturnsEmpty(FelixOsgiLauncher launcher) {
        // launcher 仍处于激活状态；查询 IntegrationGreeting 已有值
        assertTrue(launcher.isActive(), "Felix 仍应激活");
    }

    private static void t_osgiPropertiesDefaultValues() {
        OsgiProperties props = new OsgiProperties();
        assertTrue(props.isEnabled(), "OSGi 默认应启用");
        assertEquals("./osgi-cache", props.getStorage(), "默认 storage 应为 ./osgi-cache");
        assertEquals(4, props.getStartLevel(), "默认 startLevel 应为 4");
        assertFalse(props.getShell().isEnabled(), "默认 shell 应禁用");
        assertEquals(6666, props.getShell().getTelnetPort(), "默认 telnet 端口应为 6666");
        assertEquals("127.0.0.1", props.getShell().getHost(), "默认 host 应为 127.0.0.1");
        System.out.println("[Real] OsgiProperties 默认值正确");
    }

    private static void t_felixStop(FelixOsgiLauncher launcher) {
        if (launcher == null) {
            return;
        }
        launcher.stop();
        assertFalse(launcher.isActive(), "Felix 停止后应处于非激活状态");
        System.out.println("[Real] Felix 已停止");
    }

    // ==================== 反射辅助 ====================

    /**
     * 判断给定符号名称的 bundle 是否已安装。
     */
    private static boolean isBundleInstalled(OsgiLauncher launcher, String symbolicName) {
        return launcher.getBundles().stream()
                .anyMatch(b -> symbolicName.equals(b.getSymbolicName()));
    }

    /**
     * 反射获取 FelixOsgiLauncher 的 framework 字段（私有），进而拿到 BundleContext。
     */
    private static Object invokeGetBundleContext(FelixOsgiLauncher launcher) {
        try {
            // FelixOsgiLauncher.framework 是私有字段，类型 org.apache.felix.framework.Felix
            // framework.getBundleContext() 返回 org.osgi.framework.BundleContext
            Class<?> launcherClass = launcher.getClass();
            // 逐层反射：launcher -> felix field -> felix.getBundleContext()
            java.lang.reflect.Field frameworkField = null;
            Class<?> current = launcherClass;
            while (current != null) {
                try {
                    frameworkField = current.getDeclaredField("framework");
                    break;
                } catch (NoSuchFieldException nsf) {
                    current = current.getSuperclass();
                }
            }
            assertNotNull(frameworkField, "未找到 framework 字段");
            frameworkField.setAccessible(true);
            Object framework = frameworkField.get(launcher);
            assertNotNull(framework, "framework 字段为 null");

            Method getBundleContext = framework.getClass().getMethod("getBundleContext");
            return getBundleContext.invoke(framework);
        } catch (Exception e) {
            throw new RuntimeException("无法获取 Felix BundleContext: " + e.getMessage(), e);
        }
    }

    /**
     * 反射调用 BundleContext.registerService(String, Object, Dictionary)。
     */
    private static void invokeBundleContextRegisterService(Object bundleContext, String interfaceName, Object service) {
        try {
            Class<?> osgiBcClass = Class.forName("org.osgi.framework.BundleContext");
            // org.osgi.framework.BundleContext.registerService(String, Object, Dictionary)
            Class<?> dictionaryClass = Class.forName("java.util.Dictionary");
            Method registerService = osgiBcClass.getMethod("registerService",
                    String.class, Object.class, dictionaryClass);
            registerService.invoke(bundleContext, interfaceName, service, null);
        } catch (Exception e) {
            throw new RuntimeException("BundleContext.registerService 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 ClassLoader 在 classpath 中查找指定 jar 的 URL。
     */
    private static URL locateJar(String jarName) {
        ClassLoader cl = OsgiRealIntegrationTest.class.getClassLoader();
        // 优先从 .m2/repository 目录查找（已知位置）
        File m2 = new File(System.getProperty("user.home"), ".m2/repository");
        if (m2.exists()) {
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(m2.toPath(), 8)) {
                java.nio.file.Path found = stream
                        .filter(p -> p.getFileName().toString().equals(jarName))
                        .findFirst().orElse(null);
                if (found != null) {
                    return found.toUri().toURL();
                }
            } catch (Exception ignored) {
            }
        }
        // 退化：classloader resource
        return cl.getResource(jarName);
    }

    /**
     * 将 URL 转为 external form（file:...）。
     */
    private static String toExternalForm(URL url) {
        try {
            return new File(url.toURI()).getAbsoluteFile().toURI().toString();
        } catch (URISyntaxException e) {
            return url.toExternalForm();
        }
    }

    // ==================== 断言工具 ====================

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertFalse(boolean cond, String msg) {
        if (cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " ==> expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) {
            throw new AssertionError(msg + " ==> expected non-null but was null");
        }
    }

    /**
     * 测试服务实现。
     */
    public static final class IntegrationGreetingImpl implements IntegrationGreeting {
        @Override
        public String greet(String name) {
            return "Hello, " + name + " (via OSGi)";
        }
    }

    /**
     * 测试结果统计。
     */
    private static final class Result {
        final String name;
        final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger pass = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.List<String> failures = Collections.synchronizedList(new java.util.ArrayList<>());

        Result(String name) {
            this.name = name;
        }

        void run(String caseName, Runnable body) {
            total.incrementAndGet();
            try {
                body.run();
                pass.incrementAndGet();
                System.out.println("  [PASS] " + caseName);
            } catch (Throwable t) {
                failures.add(caseName + " -> " + t.getMessage());
                System.out.println("  [FAIL] " + caseName + " -> " + t.getMessage());
            }
        }

        void summary() {
            System.out.println("[" + name + "] pass=" + pass.get() + "/" + total.get()
                    + (failures.isEmpty() ? "" : ", failures=" + failures));
        }
    }
}
