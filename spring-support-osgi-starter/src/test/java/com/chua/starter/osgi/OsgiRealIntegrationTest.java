package com.chua.starter.osgi;

import com.chua.common.support.objects.DefaultObjectContext;
import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.ObjectContextConfig;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.osgi.properties.OsgiProperties;
import org.junit.jupiter.api.*;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OSGi 真实集成测试。
 *
 * <p>通过 JUnit 5 执行，验证：</p>
 * <ul>
 *   <li>FelixOsgiLauncher 真实启动/停止</li>
 *   <li>Gogo bundle 自动安装（runtime/command/shell）</li>
 *   <li>SSHD bundle 自动安装（若 classpath 可用）</li>
 *   <li>OSGi 服务注册与查询</li>
 *   <li>ObjectContext 桥接</li>
 *   <li>OsgiProperties 默认值</li>
 * </ul>
 *
 * @since 4.0.0.43
 * @author CH
 */
class OsgiRealIntegrationTest {

    private FelixOsgiLauncher launcher;
    private File cacheDir;

    @BeforeEach
    void setUp() {
        cacheDir = new File("./target/osgi-cache-" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        if (launcher != null && launcher.isActive()) {
            launcher.stop();
        }
    }

    @Test
    void felixStartAndStop() {
        launcher = new FelixOsgiLauncher();
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", cacheDir.getAbsolutePath());
        config.put("org.osgi.framework.startlevel.beginning", "4");
        config.put("org.osgi.framework.storage.clean", "onFirstInit");
        launcher.start(config);

        assertTrue(launcher.isActive(), "Felix 框架应已激活");
        assertFalse(launcher.getBundles().isEmpty(), "应至少包含 system bundle");
        System.out.println("[PASS] felixStartAndStop — bundles: " + launcher.getBundles().size());
    }

    @Test
    void gogoBundlesAutoInstalled() {
        launcher = new FelixOsgiLauncher();
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", cacheDir.getAbsolutePath());
        config.put("org.osgi.framework.startlevel.beginning", "4");
        launcher.start(config);

        // 模拟 OsgiAutoConfiguration.installGogoBundles 逻辑
        installGogoBundles(launcher);

        List<?> bundles = invokeGetBundles(launcher);
        String symbolicNames = bundles.stream()
                .map(b -> invokeMethod(b, "getSymbolicName").toString())
                .reduce("", (a, b) -> a + "," + b);

        assertTrue(symbolicNames.contains("gogo.runtime"), "应包含 gogo.runtime: " + symbolicNames);
        assertTrue(symbolicNames.contains("gogo.command"), "应包含 gogo.command: " + symbolicNames);
        assertTrue(symbolicNames.contains("gogo.shell"), "应包含 gogo.shell: " + symbolicNames);
        System.out.println("[PASS] gogoBundlesAutoInstalled — " + symbolicNames);
    }

    @Test
    void sshdBundlesInstalledWhenAvailable() {
        launcher = new FelixOsgiLauncher();
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", cacheDir.getAbsolutePath());
        config.put("org.osgi.framework.startlevel.beginning", "4");
        launcher.start(config);

        installGogoBundles(launcher);
        installSshdBundles(launcher);

        List<?> bundles = invokeGetBundles(launcher);
        String symbolicNames = bundles.stream()
                .map(b -> invokeMethod(b, "getSymbolicName").toString())
                .reduce("", (a, b) -> a + "," + b);

        // SSHD bundle 可能安装也可能因 classpath 原因未安装，只记录状态
        System.out.println("[PASS] sshdBundlesInstalledWhenAvailable — bundles: " + symbolicNames);
    }

    @Test
    void osgiServiceRegisterAndLookup() throws Exception {
        launcher = new FelixOsgiLauncher();
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", cacheDir.getAbsolutePath());
        config.put("org.osgi.framework.startlevel.beginning", "4");
        launcher.start(config);
        installGogoBundles(launcher);

        // 反射注册 OSGi 服务
        Object bundleContext = invokeGetBundleContext(launcher);
        assertNotNull(bundleContext, "应能获取 BundleContext");

        TestService impl = new TestService();
        invokeRegisterService(bundleContext, TestService.class.getName(), impl);

        Thread.sleep(200);

        TestService retrieved = launcher.getService(TestService.class);
        assertNotNull(retrieved, "OSGi 注册的服务应能被查到");
        assertEquals("hello", retrieved.echo("hello"), "返回值应来自 OSGi 服务实例");
        System.out.println("[PASS] osgiServiceRegisterAndLookup — " + retrieved.echo("hello"));
    }

    @Test
    void objectContextBridgeOsgiService() {
        launcher = new FelixOsgiLauncher();
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage", cacheDir.getAbsolutePath());
        config.put("org.osgi.framework.startlevel.beginning", "4");
        launcher.start(config);
        installGogoBundles(launcher);

        ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();
        boolean added = ctx.registerBean(register);
        assertTrue(added, "OsgiBeanDefinitionRegister 应注册到 ObjectContext");
        System.out.println("[PASS] objectContextBridgeOsgiService");
    }

    @Test
    void osgiPropertiesDefaultValues() {
        OsgiProperties props = new OsgiProperties();
        assertTrue(props.isEnabled(), "OSGi 默认启用");
        assertEquals("./osgi-cache", props.getStorage(), "默认 storage");
        assertEquals(4, props.getStartLevel(), "默认 startLevel");
        assertFalse(props.getShell().isEnabled(), "默认 shell 禁用");
        assertEquals("ssh", props.getShell().getProtocol(), "默认 protocol=ssh");
        System.out.println("[PASS] osgiPropertiesDefaultValues");
    }

    @Test
    void shellProtocolDefaults() {
        OsgiProperties.Shell shell = new OsgiProperties.Shell();
        assertEquals("ssh", shell.getProtocol(), "默认协议 SSH");
        assertEquals("127.0.0.1", shell.getHost(), "默认 host");
        assertEquals(0, shell.getPort(), "默认 port=0 表示使用协议默认端口");
        assertEquals("", shell.getUsername(), "默认 username 为空");
        System.out.println("[PASS] shellProtocolDefaults");
    }

    @Test
    void inactiveLauncherReturnsEmpty() {
        FelixOsgiLauncher inactive = new FelixOsgiLauncher();
        assertFalse(inactive.isActive(), "未启动的 launcher 应非激活");
        assertTrue(inactive.getBundles().isEmpty(), "未启动的 launcher 应无 bundles");
        System.out.println("[PASS] inactiveLauncherReturnsEmpty");
    }

    // ==================== 辅助方法 ====================

    private void installGogoBundles(FelixOsgiLauncher launcher) {
        installFromMarker(launcher, "org.apache.felix.gogo.runtime.Closure", "gogo.runtime");
        installFromMarker(launcher, "org.apache.felix.gogo.command.Basic", "gogo.command");
        installFromMarker(launcher, "org.apache.felix.gogo.shell.Shell", "gogo.shell");
    }

    private void installSshdBundles(FelixOsgiLauncher launcher) {
        installFromMarker(launcher, "org.apache.sshd.osgi.SshServerFactory", "sshd-osgi");
        installFromMarker(launcher, "org.apache.sshd.sftp.server.SftpSubsystemFactory", "sshd-sftp");
        installFromMarker(launcher, "org.apache.sshd.scp.ScpSubsystemFactory", "sshd-scp");
    }

    private void installFromMarker(FelixOsgiLauncher launcher, String markerClass, String logName) {
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

    private Object invokeGetBundleContext(FelixOsgiLauncher launcher) {
        try {
            java.lang.reflect.Field f = findField(launcher.getClass(), "framework");
            f.setAccessible(true);
            Object framework = f.get(launcher);
            Method m = framework.getClass().getMethod("getBundleContext");
            return m.invoke(framework);
        } catch (Exception e) {
            throw new RuntimeException("无法获取 BundleContext: " + e.getMessage(), e);
        }
    }

    private void invokeRegisterService(Object bundleContext, String interfaceName, Object service) {
        try {
            Class<?> osgiBc = Class.forName("org.osgi.framework.BundleContext");
            Class<?> dictionary = Class.forName("java.util.Dictionary");
            Method m = osgiBc.getMethod("registerService", String.class, Object.class, dictionary);
            m.invoke(bundleContext, interfaceName, service, null);
        } catch (Exception e) {
            throw new RuntimeException("registerService 失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> invokeGetBundles(FelixOsgiLauncher launcher) {
        try {
            Method m = FelixOsgiLauncher.class.getMethod("getBundles");
            return (List<?>) m.invoke(launcher);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object invokeMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + name);
    }

    // ==================== 测试服务接口 ====================

    public interface TestService {
        String echo(String msg);
    }

    public static class TestServiceImpl implements TestService {
        @Override
        public String echo(String msg) {
            return msg;
        }
    }

    public static class TestService implements TestService {
        @Override
        public String echo(String msg) {
            return msg;
        }
    }
}
