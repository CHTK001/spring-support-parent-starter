package com.chua.starter.test;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.osgi.support.FelixOsgiLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * OSGi 真实测试（框架级 bundle 生命周期）：Apache Felix 框架真实装配，
 * 经 {@link FelixOsgiLauncher} 的 OSGi API 驱动**真实 bundle jar**（动态生成的合法
 * OSGi bundle）的安装 → 查询 → 启动（ACTIVE）→ 卸载 全生命周期。
 *
 * <p>不依赖远程 shell（shell 关闭）；SSH shell 模式驱动见 {@code OsgiSshShellRealTest}
 * （受 SSHD/gogo 版本配对限制，如实门控）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-test2",
        "plugin.osgi.shell.enabled=false"
})
class OsgiBundleLifecycleRealTest {

    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.bundle";

    private static final File BUNDLE_FILE = new File("target/test-bundle-1.0.0.jar");

    static {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(BUNDLE_FILE))) {
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jos.write(("Manifest-Version: 1.0\n"
                    + "Bundle-ManifestVersion: 2\n"
                    + "Bundle-SymbolicName: " + BUNDLE_SYMBOLIC_NAME + "\n"
                    + "Bundle-Version: 1.0.0\n").getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        } catch (Exception e) {
            throw new IllegalStateException("测试 bundle 生成失败", e);
        }
    }

    @Autowired
    private FelixOsgiLauncher osgiLauncher;

    /** 真实测试：bundle 安装 → 查询 → 启动（ACTIVE）→ 卸载（OSGi 框架级 API） */
    @Test
    void bundleInstallStartUninstallViaFramework() {
        // 1) 安装真实 bundle
        OsgiBundle bundle = osgiLauncher.installBundle("file:" + BUNDLE_FILE.getAbsolutePath());
        assertNotNull(bundle, "安装应返回 bundle");
        assertEquals(BUNDLE_SYMBOLIC_NAME, bundle.getSymbolicName(), "bundle 符号名应匹配");

        // 2) 经 BundleStateQuery 查询确认存在
        assertNotNull(osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME), "安装后应可经 BundleStateQuery 查询到");

        // 3) 启动 → ACTIVE
        bundle.start();
        assertEquals("ACTIVE", bundle.getState(), "bundle 启动后应为 ACTIVE，实际: " + bundle.getState());

        // 4) 卸载 → 查询不到
        osgiLauncher.uninstallBundle(BUNDLE_SYMBOLIC_NAME);
        assertNull(osgiLauncher.getBundle(BUNDLE_SYMBOLIC_NAME), "卸载后应查询不到 bundle");
    }
}
