package com.chua.starter.bundle;

import com.chua.starter.bundle.service.BundleManagerService;
import com.chua.starter.bundle.properties.BundleProperties;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * spring-support-bundle-starter 真实测试入口。
 *
 * <p>验证 BundleManagerService 的完整功能：</p>
 * <ul>
 *   <li>Maven 坐标安装 bundle</li>
 *   <li>版本升级检测</li>
 *   <li>Bundle 状态查询</li>
 *   <li>Bundle 卸载</li>
 *   <li>属性默认值验证</li>
 * </ul>
 *
 * @since 4.0.0.43
 * @author CH
 */
public class BundleManagerTest {

    public static void main(String[] args) {
        Result r = new Result("BundleManagerTest");
        final Object[] launcherHolder = new Object[1];

        try {
            // ========== 初始化 ==========
            com.chua.osgi.support.FelixOsgiLauncher launcher = new com.chua.osgi.support.FelixOsgiLauncher();
            launcher.setAutoStartInstalledBundles(false);
            launcherHolder[0] = launcher;
            Map<String, String> config = new HashMap<>();
            config.put("org.osgi.framework.storage",
                    new File("./target/osgi-cache-bundle-test").getAbsolutePath());
            config.put("org.osgi.framework.startlevel.beginning", "4");
            config.put("org.osgi.framework.storage.clean", "onFirstInit");
            launcher.start(config);
            assertTrue(launcher.isActive(), "Felix 框架应已激活");
            System.out.println("[Real] Felix 已激活，bundles: " + launcher.getBundles().size());

            BundleProperties props = new BundleProperties();
            props.setEnabled(true);
            props.setRepositoryUrl("https://repo1.maven.org/maven2");

            BundleManagerService manager = new BundleManagerService(launcher, props);

            // ========== 测试 1: 安装 Gogo runtime ==========
            r.run("install_gogoRuntime", () -> {
                BundleManagerService.InstallResult result = manager.install("org.apache.felix:org.apache.felix.gogo.runtime:1.1.6");
                assertTrue(result.success(), "gogo.runtime 应安装成功: " + result.message());
                assertTrue(manager.isInstalled("org.apache.felix.gogo.runtime"), "gogo.runtime 应存在于 bundle 列表中");
                System.out.println("  [INFO] 已安装: " + result.artifactId() + ":" + result.version());
            });

            // ========== 测试 2: 安装 Gogo command ==========
            r.run("install_gogoCommand", () -> {
                BundleManagerService.InstallResult result = manager.install("org.apache.felix:org.apache.felix.gogo.command:1.1.2");
                assertTrue(result.success(), "gogo.command 应安装成功: " + result.message());
                System.out.println("  [INFO] 已安装: " + result.artifactId() + ":" + result.version());
            });

            // ========== 测试 3: 重复安装同一版本应返回成功但无变化 ==========
            r.run("reinstall_sameVersion", () -> {
                BundleManagerService.InstallResult result = manager.install("org.apache.felix:org.apache.felix.gogo.runtime:1.1.6");
                assertTrue(result.success(), "重复安装应返回成功（已是最新版）");
                System.out.println("  [INFO] 重复安装识别正确: " + result.artifactId());
            });

            // ========== 测试 4: 获取 Bundle 状态 ==========
            r.run("get_bundleStatus", () -> {
                List<Map<String, Object>> status = manager.getStatus();
                assertFalse(status.isEmpty(), "应有 bundle 状态信息");
                boolean hasGogoRuntime = status.stream()
                        .anyMatch(b -> b.get("symbolicName").toString().contains("gogo.runtime"));
                assertTrue(hasGogoRuntime, "状态列表中应包含 gogo.runtime");
                System.out.println("  [INFO] Bundle 状态: " + status.size() + " 个");
                for (Map<String, Object> b : status) {
                    System.out.println("    - " + b.get("symbolicName") + " [" + b.get("state") + "] managed=" + b.get("managed"));
                }
            });

            // ========== 测试 5: 卸载 bundle ==========
            r.run("uninstall_bundle", () -> {
                boolean removed = manager.uninstall("org.apache.felix.gogo.command");
                assertTrue(removed, "gogo.command 应成功卸载");
                assertFalse(manager.isInstalled("org.apache.felix.gogo.command"), "卸载后 gogo.command 不应存在");
                System.out.println("  [INFO] bundle 卸载成功");
            });

            // ========== 测试 6: 属性默认值验证 ==========
            r.run("propertiesDefaults", () -> {
                BundleProperties p = new BundleProperties();
                assertFalse(p.isEnabled(), "默认 enabled=false");
                assertTrue(p.isAutoUpgrade(), "默认 autoUpgrade=true");
                assertEquals("https://repo1.maven.org/maven2", p.getRepositoryUrl(), "默认 repositoryUrl");
                System.out.println("  [INFO] 属性默认值验证通过");
            });

        } catch (Throwable t) {
            r.failures.add("bootstrap -> " + t.getMessage());
            System.err.println("  [FAIL] bootstrap -> " + t.getMessage());
            t.printStackTrace();
        } finally {
            r.run("frameworkStop", () -> {
                com.chua.osgi.support.FelixOsgiLauncher launcher = (com.chua.osgi.support.FelixOsgiLauncher) launcherHolder[0];
                if (launcher != null) {
                    launcher.stop();
                    assertTrue(!launcher.isActive(), "Felix 停止后应非激活");
                    System.out.println("  [INFO] Felix 已停止");
                }
            });
        }

        r.summary();
        if (!r.failures.isEmpty()) {
            System.err.println("\nFAILURES:");
            for (String f : r.failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // ==================== 辅助 ====================

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
