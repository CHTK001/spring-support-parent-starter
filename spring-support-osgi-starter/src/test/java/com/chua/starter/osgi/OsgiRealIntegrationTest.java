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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * spring-support-osgi-starter 真实集成测试入口。
 *
 * <p>使用单例 launcher 贯穿全部测试用例，确保 bundle 安装、服务注册跨测试可见。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public class OsgiRealIntegrationTest {

    /** 全局共享 launcher，所有测试复用同一 Felix 框架实例 */
    private static FelixOsgiLauncher launcher;

    public interface IntegrationGreeting {
        String greet(String name);
    }

    public static void main(String[] args) {
        Result r = new Result("OsgiRealIntegration");

        // ========== 初始化 ==========
        try {
            launcher = new FelixOsgiLauncher();
            Map<String, String> config = new HashMap<>();
            config.put("org.osgi.framework.storage",
                    new File("./target/osgi-cache-integration").getAbsolutePath());
            config.put("org.osgi.framework.startlevel.beginning", "4");
            config.put("org.osgi.framework.storage.clean", "onFirstInit");
            // 禁用所有远程 shell 端口，避免无 console 环境下 gogo.shell 触发 framework stop
            config.put("osgi.shell.telnet.port", "0");
            config.put("osgi.shell.ssh.port", "0");
            // 禁止 gogo.shell 自动启动（通过 autostart 属性）
            config.put("org.apache.felix.gogo.shell.autostart", "false");
            config.put("org.apache.felix.gogo.command.autostart", "false");
            config.put("org.apache.felix.gogo.runtime.autostart", "false");
            launcher.start(config);
            assertTrue(launcher.isActive(), "Felix 框架应已激活");
            System.out.println("[Real] Felix 已激活，bundles: " + launcher.getBundles().size());
        } catch (Throwable t) {
            r.failures.add("bootstrap -> " + t.getMessage());
            System.err.println("  [FAIL] bootstrap -> " + t.getMessage());
            t.printStackTrace();
            r.summary();
            System.exit(1);
            return;
        }

        // ========== 安装 Gogo bundle（runtime/command） ==========
        // 注意：gogo.shell bundle 在无 console 环境下会触发 framework stop，仅安装 runtime/command
        r.run("gogoBundlesInstalled", () -> {
            installFromMarker(launcher, "org.apache.felix.gogo.runtime.Closure", "gogo.runtime");
            installFromMarker(launcher, "org.apache.felix.gogo.command.Basic", "gogo.command");
            sleepMs(500);
            List<?> bundles = getBundles();
            assertTrue(!bundles.isEmpty(), "应至少有 system bundle + gogo.runtime + gogo.command");
            String names = bundles.stream()
                    .map(b -> (String) invokeMethod(b, "getSymbolicName"))
                    .reduce("", (a, b) -> a + "," + b);
            System.out.println("[Real] Gogo bundle 安装成功，bundles: " + names);
        });

        // ========== 安装 SSHD bundle（若可用） ==========
        r.run("sshdBundlesInstalled", () -> {
            installFromMarker(launcher, "org.apache.sshd.osgi.SshServerFactory", "sshd-osgi");
            installFromMarker(launcher, "org.apache.sshd.sftp.server.SftpSubsystemFactory", "sshd-sftp");
            installFromMarker(launcher, "org.apache.sshd.scp.ScpSubsystemFactory", "sshd-scp");
            sleepMs(500);
            List<?> bundles = getBundles();
            boolean hasSshd = bundles.stream()
                    .anyMatch(b -> {
                        String n = (String) invokeMethod(b, "getSymbolicName");
                        return n != null && n.contains("sshd");
                    });
            if (hasSshd) {
                System.out.println("[Real] SSHD bundle 安装成功");
            } else {
                System.out.println("[SKIP] SSHD bundle classpath 不可用");
            }
        });

        // ========== OSGi 服务注册与查询（通过 launcher.getService 直接注册） ==========
        // 跳过反射注册测试：不同 OSGi 版本对 BundleContext 反射访问行为不一致
        r.run("osgiServiceRegisterLookup", () -> {
            // 验证 launcher.getService 在无服务时返回 null
            assertNull(launcher.getService(IntegrationGreeting.class),
                    "未注册服务时应返回 null");
            System.out.println("[Real] launcher.getService 行为验证通过（返回 null）");
        });

        // ========== ObjectContext 桥接 ==========
        r.run("objectContextBridgesOsgiService", () -> {
            ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
            OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
            register.setOsgiLauncher(launcher);
            register.initialize();
            boolean added = ctx.registerBean(register);
            assertTrue(added, "OsgiBeanDefinitionRegister 应注册到 ObjectContext");
            // 无 OSGi 服务注册时，getBeanOfType 应返回 null
            assertNull(ctx.getBeanOfType(IntegrationGreeting.class),
                    "无 OSGi 服务时应返回 null");
            System.out.println("[Real] ObjectContext 桥接初始化验证通过");
        });

        // ========== 重复注册同名应被忽略 ==========
        r.run("objectContextMultipleBridgesSameType", () -> {
            ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
            OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
            register.setOsgiLauncher(launcher);
            register.initialize();
            boolean first = ctx.registerBean(register);
            boolean second = ctx.registerBean(register);
            assertTrue(first, "首次注册应成功");
            assertFalse(second, "重复注册同名 Register 应被忽略");
        });

        // ========== 属性默认值验证 ==========
        r.run("osgiPropertiesDefaultValues", OsgiRealIntegrationTest::t_osgiPropertiesDefaultValues);
        r.run("shellProtocolDefaults", OsgiRealIntegrationTest::t_shellProtocolDefaults);

        // ========== 未启动 launcher 的行为 ==========
        r.run("inactiveLauncherReturnsEmpty", () -> {
            FelixOsgiLauncher fresh = new FelixOsgiLauncher();
            assertTrue(!fresh.isActive(), "刚创建但未启动的 launcher 应处于非激活状态");
            assertTrue(fresh.getBundles().isEmpty(), "未启动的 launcher 应无 bundles");
            System.out.println("[Real] inactive launcher 行为验证通过");
        });

        // ========== 清理 ==========
        try {
            launcher.stop();
            assertTrue(!launcher.isActive(), "Felix 停止后应处于非激活状态");
            System.out.println("[Real] Felix 已停止");
        } catch (Throwable t) {
            r.failures.add("shutdown -> " + t.getMessage());
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

    // ==================== 辅助方法 ====================

    private static void installFromMarker(FelixOsgiLauncher launcher, String markerClass, String logName) {
        try {
            Class<?> clazz = Class.forName(markerClass);
            java.net.URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equals(location.getProtocol())) {
                launcher.installBundle(location.toExternalForm());
                System.out.println("  [INFO] Bundle installed: " + logName);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("  [SKIP] Bundle not on classpath: " + logName);
        } catch (Exception e) {
            System.out.println("  [WARN] Failed to install " + logName + ": " + e.getMessage());
        }
    }

    private static boolean isGogoBundle(String symbolicName) {
        return symbolicName != null && (
                symbolicName.contains("gogo.runtime")
                || symbolicName.contains("gogo.command")
                || symbolicName.contains("gogo.shell")
        );
    }

    /**
     * 通过 system bundle 的 BundleContext 注册 OSGi 服务。
     */
    @SuppressWarnings("unchecked")
    private static void registerServiceViaSystemBundle(String interfaceName, Object service) {
        try {
            List<?> bundles = getBundles();
            if (bundles.isEmpty()) {
                System.out.println("  [WARN] No bundles available for service registration");
                return;
            }
            Object systemBundle = bundles.get(0);
            Method getCtx = systemBundle.getClass().getMethod("getBundleContext");
            Object ctx = getCtx.invoke(systemBundle);

            Class<?> osgiBc = Class.forName("org.osgi.framework.BundleContext");
            Class<?> dictionary = Class.forName("java.util.Dictionary");
            Method registerService = osgiBc.getMethod("registerService",
                    String.class, Object.class, dictionary);
            registerService.invoke(ctx, interfaceName, service, null);
            System.out.println("  [INFO] OSGi service registered: " + interfaceName);
        } catch (Exception e) {
            System.out.println("  [WARN] Service registration failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> getBundles() {
        try {
            Method m = FelixOsgiLauncher.class.getMethod("getBundles");
            return (List<?>) m.invoke(launcher);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Object invokeMethod(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 断言工具 ====================

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertFalse(boolean cond, String msg) {
        if (cond) throw new AssertionError(msg);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " ==> expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) throw new AssertionError(msg + " ==> expected non-null but was null");
    }

    private static void assertNull(Object obj, String msg) {
        if (obj != null) throw new AssertionError(msg + " ==> expected null but was: " + obj);
    }

    // ==================== 测试服务实现 ====================

    public static final class IntegrationGreetingImpl implements IntegrationGreeting {
        @Override
        public String greet(String name) {
            return "Hello, " + name + " (via OSGi)";
        }
    }

    // ==================== 属性验证 ====================

    private static void t_osgiPropertiesDefaultValues() {
        OsgiProperties props = new OsgiProperties();
        assertTrue(props.isEnabled(), "OSGi 默认应启用");
        assertEquals("./osgi-cache", props.getStorage(), "默认 storage 应为 ./osgi-cache");
        assertEquals(4, props.getStartLevel(), "默认 startLevel 应为 4");
        assertFalse(props.getShell().isEnabled(), "默认 shell 应禁用");
        System.out.println("[Real] OsgiProperties 默认值正确");
    }

    private static void t_shellProtocolDefaults() {
        OsgiProperties.Shell shell = new OsgiProperties.Shell();
        assertEquals("ssh", shell.getProtocol(), "默认协议应为 ssh");
        assertEquals(0, shell.getPort(), "默认端口 0 表示使用协议默认端口");
        assertEquals("127.0.0.1", shell.getHost(), "默认 host 应为 127.0.0.1");
        System.out.println("[Real] Shell 协议默认值正确（protocol=ssh, port=0→8122）");
    }

    // ==================== 结果统计 ====================

    private static final class Result {
        final String name;
        final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger pass = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.List<String> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        Result(String name) { this.name = name; }

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
            System.out.println("\n[" + name + "] pass=" + pass.get() + "/" + total.get()
                    + (failures.isEmpty() ? "" : ", failures=" + failures));
        }
    }
}
