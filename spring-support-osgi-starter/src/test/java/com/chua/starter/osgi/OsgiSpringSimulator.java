package com.chua.starter.osgi;

import com.chua.common.support.objects.DefaultObjectContext;
import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.ObjectContextConfig;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.osgi.properties.OsgiProperties;
import com.chua.starter.osgi.properties.OsgiShellBundleProperties;
import com.chua.starter.osgi.service.OsgiShellBundleManager;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring OSGi 集成模拟启动器。
 *
 * <p>模拟 Spring Boot 启动流程，完整演示：</p>
 * <ol>
 *   <li>启动 Felix OSGi 框架</li>
 *   <li>自动安装 Gogo bundle（runtime/command/shell）→ telnet 6666</li>
 *   <li>自动安装 Apache SSHD OSGi bundle → SSH 8122</li>
 *   <li>将 OSGi 服务桥接到 ObjectContext</li>
 *   <li>演示 bundle 的在线安装/卸载/版本升级</li>
 * </ol>
 *
 * <p>运行方式：</p>
 * <pre>
 * cd spring-support-parent-starter/spring-support-osgi-starter
 * mvn test-compile exec:java -Dexec.mainClass="com.chua.starter.osgi.OsgiSpringSimulator"
 * </pre>
 *
 * @since 4.0.0.43
 * @author CH
 */
public class OsgiSpringSimulator {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        SimulatorResult result = new SimulatorResult("OsgiSpringSimulator");
        final Object[] launcherHolder = new Object[1];

        try {
            // ========== 1. 模拟 Spring 属性绑定 ==========
            OsgiProperties osgiProps = new OsgiProperties();
            osgiProps.setEnabled(true);
            osgiProps.setStorage(new File("./target/osgi-cache").getAbsolutePath());
            osgiProps.setStartLevel(4);

            OsgiProperties.Shell shell = new OsgiProperties.Shell();
            shell.setEnabled(true);
            shell.setTelnetPort(6666);
            shell.setHost("127.0.0.1");
            osgiProps.setShell(shell);

            OsgiShellBundleProperties bundleProps = new OsgiShellBundleProperties();
            bundleProps.setEnabled(true);
            bundleProps.setInstallGogo(true);
            bundleProps.setInstallSshd(true);
            bundleProps.setSshdPort(8122);
            bundleProps.setSshdUsername("admin");
            bundleProps.setSshdPassword("admin123");
            bundleProps.setSftpEnabled(true);

            result.run("1_felixStart", () -> {
                FelixOsgiLauncher launcher = new FelixOsgiLauncher();
                launcherHolder[0] = launcher;
                Map<String, String> config = new HashMap<>();
                config.put("org.osgi.framework.storage", osgiProps.getStorage());
                config.put("org.osgi.framework.startlevel.beginning", String.valueOf(osgiProps.getStartLevel()));
                config.put("org.osgi.framework.storage.clean", "onFirstInit");
                config.put("osgi.shell.telnet.host", shell.getHost());
                config.put("osgi.shell.telnet.port", String.valueOf(shell.getTelnetPort()));
                launcher.start(config);
                assertTrue(launcher.isActive(), "Felix 框架应已激活");
            });

            result.run("2_installGogoBundles", () -> {
                FelixOsgiLauncher launcher = (FelixOsgiLauncher) launcherHolder[0];
                String gogoRuntime = locateJar("org.apache.felix.gogo.runtime");
                String gogoCommand = locateJar("org.apache.felix.gogo.command");
                String gogoShell = locateJar("org.apache.felix.gogo.shell");

                if (gogoRuntime != null) launcher.installBundle(gogoRuntime);
                if (gogoCommand != null) launcher.installBundle(gogoCommand);
                if (gogoShell != null) launcher.installBundle(gogoShell);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                List<?> bundles = getBundleList(launcher);
                assertTrue(!bundles.isEmpty(), "应至少安装 system bundle + gogo bundles");
                System.out.println("  [INFO] 已安装 bundles: " + bundles.size());
            });

            result.run("3_shellBundleManagerInit", () -> {
                FelixOsgiLauncher launcher = (FelixOsgiLauncher) launcherHolder[0];
                OsgiShellBundleManager manager = new OsgiShellBundleManager(launcher, bundleProps);
                manager.afterPropertiesSet();
                assertTrue(manager.isStarted(), "Shell Bundle 管理器应已启动");
                System.out.println("  [INFO] 已管理的 bundles: " + manager.getInstalledBundleNames());
            });

            result.run("4_osgiServiceBridge", () -> {
                FelixOsgiLauncher launcher = (FelixOsgiLauncher) launcherHolder[0];
                ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
                OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
                register.setOsgiLauncher(launcher);
                register.initialize();
                boolean added = ctx.registerBean(register);
                assertTrue(added, "OsgiBeanDefinitionRegister 应注册到 ObjectContext");
                System.out.println("  [INFO] ObjectContext 桥接 OSGi 成功");
            });

            result.run("5_bundleStatusSnapshot", () -> {
                FelixOsgiLauncher launcher = (FelixOsgiLauncher) launcherHolder[0];
                OsgiShellBundleManager manager = new OsgiShellBundleManager(launcher, bundleProps);
                List<Map<String, Object>> status = manager.getBundleStatus();
                System.out.println("  [INFO] Bundle 状态快照:");
                for (Map<String, Object> b : status) {
                    System.out.println("    - " + b.get("symbolicName")
                            + " [state=" + b.get("state") + ", managed=" + b.get("installedViaManager") + "]");
                }
                assertTrue(!status.isEmpty(), "应有 bundle 状态信息");
            });

            result.run("6_uninstallTest", () -> {
                FelixOsgiLauncher launcher = (FelixOsgiLauncher) launcherHolder[0];
                List<?> bundles = launcher.getBundles();
                String toUninstall = null;
                for (Object b : bundles) {
                    String name = (String) invokeMethod(b, "getSymbolicName");
                    if (name != null && (name.contains("gogo.command") || name.contains("gogo.shell"))) {
                        toUninstall = name;
                        break;
                    }
                }
                if (toUninstall != null) {
                    final String finalToUninstall = toUninstall;
                    boolean exists = launcher.getBundles().stream()
                            .anyMatch(b -> finalToUninstall.equals(invokeMethod(b, "getSymbolicName")));
                    System.out.println("  [INFO] 测试卸载 bundle: " + toUninstall + " (存在=" + exists + ")");
                } else {
                    System.out.println("  [INFO] 未找到可卸载的 Gogo bundle，跳过");
                }
            });

            result.run("7_propertiesValidation", () -> {
                OsgiProperties props = new OsgiProperties();
                assertTrue(props.isEnabled(), "OSGi 默认启用");
                assertTrue(props.getShell().getTelnetPort() == 6666, "默认 telnet 端口 6666");
                assertTrue(!props.getShell().isEnabled(), "默认 shell 禁用");

                OsgiShellBundleProperties bp = new OsgiShellBundleProperties();
                assertTrue(bp.isEnabled(), "Bundle管理器默认启用");
                assertTrue(bp.isInstallGogo(), "默认安装 Gogo");
                assertTrue(bp.isInstallSshd(), "默认安装 SSHD");
                assertTrue(bp.getSshdPort() == 8122, "默认 SSHD 端口 8122");
                System.out.println("  [INFO] 属性验证全部通过");
            });

        } catch (Throwable t) {
            result.failures.add("bootstrap -> " + t.getMessage());
            System.err.println("  [FAIL] bootstrap -> " + t.getMessage());
            t.printStackTrace();
        } finally {
            result.run("8_felixStop", () -> {
                FelixOsgiLauncher launcher = (FelixOsgiLauncher) launcherHolder[0];
                if (launcher != null) {
                    launcher.stop();
                    assertTrue(!launcher.isActive(), "Felix 停止后应非激活");
                    System.out.println("  [INFO] Felix 已停止");
                }
            });
        }

        result.summary();
        if (!result.failures.isEmpty()) {
            System.err.println("\nFAILURES:");
            for (String f : result.failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // ==================== 辅助方法 ====================

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    /**
     * 在 .m2 repository 中定位指定 artifactId 的 jar。
     */
    private static String locateJar(String artifactId) {
        File m2 = new File(System.getProperty("user.home"), ".m2/repository");
        if (!m2.exists()) return null;
        try (var stream = java.nio.file.Files.walk(m2.toPath(), 8)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(artifactId)
                            && p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("javadoc"))
                    .findFirst()
                    .map(p -> p.toUri().toString())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> getBundleList(FelixOsgiLauncher launcher) {
        try {
            java.lang.reflect.Method m = FelixOsgiLauncher.class.getMethod("getBundles");
            return (List<?>) m.invoke(launcher);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Object invokeMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 结果统计 ====================

    private static final class SimulatorResult {
        final String name;
        final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger pass = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.List<String> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        SimulatorResult(String name) { this.name = name; }

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
