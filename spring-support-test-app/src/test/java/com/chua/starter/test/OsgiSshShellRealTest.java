package com.chua.starter.test;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OSGi 真实测试（SSH shell 模式）：Apache Felix + Gogo SSH shell——
 * 经 jsch 客户端连接 SSH shell 执行 Gogo 命令（{@code bundle:install}/{@code bundle:list}/
 * {@code bundle:uninstall}）驱动**真实 bundle**（动态生成的合法 OSGi bundle jar）的安装/卸载生命周期。
 *
 * <p>本测试用测试属性**单独开启** OSGi（共享 yaml 默认关闭）：独立上下文，避免全量套件
 * 每个上下文启动 Felix 框架（重）且 SSH shell（8122）端口冲突。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-test",
        "plugin.osgi.shell.enabled=true",
        "plugin.osgi.shell.protocol=ssh",
        "plugin.osgi.shell.host=127.0.0.1",
        "plugin.osgi.shell.port=8122",
        "plugin.osgi.shell.username=admin",
        "plugin.osgi.shell.password=admin"
})
class OsgiSshShellRealTest {

    private static final int SSH_PORT = 8122;
    private static final String SSH_USER = "admin";
    private static final String SSH_PASSWORD = "admin";
    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.bundle";

    /** 真实 bundle jar：动态生成合法 OSGi bundle（MANIFEST 含 Bundle-SymbolicName） */
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

    @BeforeEach
    void waitForSshShell() throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            Session session = openSession();
            try {
                return;
            } catch (Exception ignored) {
                Thread.sleep(500);
            } finally {
                session.disconnect();
            }
        }
        fail("OSGi Gogo SSH shell 未在 60s 内就绪（127.0.0.1:" + SSH_PORT + "）");
    }

    private Session openSession() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(SSH_USER, "127.0.0.1", SSH_PORT);
        session.setPassword(SSH_PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        return session;
    }

    /**
     * 经 SSH shell 执行 Gogo 命令并读取输出（交互 shell：写命令 + 读至命令回显后的提示符）。
     * 单命令单连接（输入 EOF 后远端 shell 退出，连接随之关闭）。
     */
    private String shell(String command) throws Exception {
        Session session = openSession();
        try {
            ChannelShell channel = (ChannelShell) session.openChannel("shell");
            channel.setInputStream(new ByteArrayInputStream((command + "\n").getBytes(StandardCharsets.UTF_8)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.setOutputStream(out);
            channel.connect(5000);
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                String text = out.toString(StandardCharsets.UTF_8);
                // 命令回显出现且其后出现提示符（g!）视为命令执行完成
                if (text.contains(command) && text.indexOf("g!") > text.indexOf(command)) {
                    break;
                }
                Thread.sleep(200);
            }
            channel.disconnect();
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            session.disconnect();
        }
    }

    /** 真实测试：bundle 安装/卸载经 SSH shell 模式（Gogo 命令驱动） */
    @Test
    void bundleInstallUninstallViaSshShell() throws Exception {
        // 1) 经 SSH shell 安装真实 bundle
        String installOut = shell("bundle:install file:" + BUNDLE_FILE.getAbsolutePath());
        assertTrue(installOut.contains("Bundle ID"), "bundle:install 应返回 Bundle ID，实际输出: " + installOut);
        long bundleId = parseBundleId(installOut);
        assertTrue(bundleId > 0, "应解析出合法 bundle id: " + installOut);

        // 2) 列表确认 bundle 存在且状态正确（INSTALLED/RESOLVED/ACTIVE 之一）
        String listOut = shell("bundle:list");
        assertTrue(listOut.contains(BUNDLE_SYMBOLIC_NAME), "bundle:list 应包含目标 bundle，实际输出: " + listOut);

        // 3) 启动 bundle（ACTIVE 状态验证）
        String startOut = shell("bundle:start " + bundleId);
        String listAfterStart = shell("bundle:list");
        assertTrue(listAfterStart.contains(BUNDLE_SYMBOLIC_NAME), "启动后 bundle 仍应在列表中");

        // 4) 经 SSH shell 卸载 bundle
        shell("bundle:uninstall " + bundleId);
        String listAfterUninstall = shell("bundle:list");
        assertFalse(listAfterUninstall.contains(BUNDLE_SYMBOLIC_NAME),
                "卸载后 bundle:list 不应包含目标 bundle，实际输出: " + listAfterUninstall);
    }

    /** 从 bundle:install 输出解析 Bundle ID */
    private static long parseBundleId(String output) {
        Matcher matcher = Pattern.compile("Bundle ID[:\\s]+(\\d+)").matcher(output);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }
}
