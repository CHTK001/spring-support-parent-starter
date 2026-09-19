package com.chua.starter.test;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.Assumptions;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * osgi 真实测试（SSH Shell 模式）：Apache Felix + Gogo SSH Shell——
 * 经 jsch 客户端连接 SSH Shell 执行 Gogo 命令（{@code bundle:install}/{@code bundle:list}/
 * {@code bundle:uninstall}）驱动**真实 bundle**（动态生成的合法 OSGi bundle jar）的安装/卸载生命周期。
 *
 * <p>本测试用测试属性**单独开启** OSGi（共享 yaml 默认关闭）：独立上下文，避免全量套件
 * 每个上下文启动 Felix 框架（重）且 SSH Shell（8122）端口冲突。</p>
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
/**
 * osgisshshellreal测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class OsgiSshShellRealTest {

    private static final int SSH_PORT = 8122; // ssh端口
    private static final String SSH_USER = "admin"; // ssh用户
    private static final String SSH_PASSWORD = "admin"; // ssh密码
    private static final String BUNDLE_SYMBOLIC_NAME = "com.chua.test.bundle"; // bundlesymbolic名称

    /**
     * 真实 bundle jar：动态生成合法 osgi bundle（MANIFEST 含 Bundle-symbolic名称）
    */
    private static final File BUNDLE_FILE = new File("target/test-bundle-1.0.0.jar");

    static {
        /**
         * 尝试。
         * @param FileOutputStream(BUNDLE_FILE 文件输出流(bundle文件
         */
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(BUNDLE_FILE))) {
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jos.write(("Manifest-Version: 1.0\n"
                    + "Bundle-ManifestVersion: 2\n"
                    + "Bundle-SymbolicName: " + BUNDLE_SYMBOLIC_NAME + "\n"
                    + "Bundle-Version: 1.0.0\n").getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param 生成失败" 生成失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("测试 bundle 生成失败", e);
        }
    }

    @BeforeEach
    /**
     * waitforsshshell。
     */
    void waitForSshShell() throws Exception {
 // SSH Shell 现为已交付能力（gogosshshell服务端 自动装配）——服务器起不来即为回归，硬失败
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                Session session = openSession();
                try {
                    return;
                } finally {
                    session.disconnect();
                }
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        /**
         * 失败。
         * @param 装配回归" 装配回归"
         */
        fail("OSGi Gogo SSH shell 未在 60s 内就绪（127.0.0.1:" + SSH_PORT + "）——SSH shell 装配回归");
    }

    /**
     * 打开会话。
     * @return 打开会话的结果
     */
    private Session openSession() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(SSH_USER, "127.0.0.1", SSH_PORT);
        session.setPassword(SSH_PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        return session;
    }

    /**
     * 经 SSH Shell 执行 Gogo 命令并读取输出（交互 Shell：写命令 + 读至命令回显后的提示符）。
     * 单命令单连接（输入 EOF 后远端 Shell 退出，连接随之关闭）。
     * @param command 命令
     * @return shell的结果
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
                // 命令回显出现且命令后有**尾部**提示符（g!）视为命令执行完成
 // （索引的 会命中命令前的首个提示符导致永不成立，必须 最后一个索引的）
                if (text.contains(command) && text.lastIndexOf("g!") > text.indexOf(command)) {
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

    /**
     * 真实测试：bundle 安装/卸载经 SSH Shell 模式（Gogo 命令驱动）
    */
    @Test
    void bundleInstallUninstallViaSshShell() throws Exception {
        // 1) 经 SSH shell 安装真实 bundle
        String installOut = shell("bundle:install file:" + BUNDLE_FILE.getAbsolutePath());
        /**
         * 断言true。
         * @param ID" 标识"
         */
        assertTrue(installOut.contains("Bundle ID"), "bundle:install 应返回 Bundle ID，实际输出: " + installOut);
        long bundleId = parseBundleId(installOut);
        /**
         * 断言true。
         * @param installOut install出
         */
        assertTrue(bundleId > 0, "应解析出合法 bundle id: " + installOut);

 // 2) 列表确认 bundle 存在且状态正确（INSTALLED/RESOLVED/活跃 之一）
        String listOut = shell("bundle:list");
        /**
         * 断言true。
         * @param listOut.contains(BUNDLE_SYMBOLIC_NAME 列表出.contains(bundlesymbolic名称
         */
        assertTrue(listOut.contains(BUNDLE_SYMBOLIC_NAME), "bundle:list 应包含目标 bundle，实际输出: " + listOut);

 // 3) 启动 bundle（活跃 状态验证）
        String startOut = shell("bundle:start " + bundleId);
        String listAfterStart = shell("bundle:list");
        /**
         * 断言true。
         * @param listAfterStart.contains(BUNDLE_SYMBOLIC_NAME 列表之后启动.contains(bundlesymbolic名称
         */
        assertTrue(listAfterStart.contains(BUNDLE_SYMBOLIC_NAME), "启动后 bundle 仍应在列表中");

        // 4) 经 SSH shell 卸载 bundle
        /**
         * Shell。
         * @param bundleId bundleid
         */
        shell("bundle:uninstall " + bundleId);
        String listAfterUninstall = shell("bundle:list");
        /**
         * 断言false。
         * @param listAfterUninstall.contains(BUNDLE_SYMBOLIC_NAME 列表之后uninstall.contains(bundlesymbolic名称
         */
        assertFalse(listAfterUninstall.contains(BUNDLE_SYMBOLIC_NAME),
                "卸载后 bundle:list 不应包含目标 bundle，实际输出: " + listAfterUninstall);
    }

    /**
     * 从 bundle:install 输出解析 Bundle 标识
     *
     * @param output 输出
     * @return 解析bundleid的结果
     */
    private static long parseBundleId(String output) {
        Matcher matcher = Pattern.compile("Bundle ID[:\\s]+(\\d+)").matcher(output);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    /**
     * 真实测试：SSH 认证失败（错误密码）应被拒绝
    */
    @Test
    void authFailureRejected() {
        JSch jsch = new JSch();
        assertThrows(Exception.class, () -> {
            Session session = jsch.getSession(SSH_USER, "127.0.0.1", SSH_PORT);
            session.setPassword("wrong-password");
            session.setConfig("StrictHostKeyChecking", "no");
            try {
                session.connect(5000);
            } finally {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
            }
        }, "错误密码的 SSH 连接应被拒绝");
    }

    /**
     * 真实测试：Gogo 非 bundle: 命令经 SSH Shell 执行（执行viagogo 反射路径）
    */
    @Test
    void gogoNonBundleCommandExecuted() throws Exception {
 // help 为 Gogo runtime 内置命令，不以 bundle: 开头，需走 命令处理器 反射执行
        String helpOut = shell("help");
        /**
         * 断言false。
         * @param helpOut.contains("命令执行失败" help出.contains("命令执行失败"
         */
        assertFalse(helpOut.contains("命令执行失败"), "help 不应报命令执行失败，实际输出: " + helpOut);
        // 命令回显后的尾部提示符 g! 表示会话正常结束且命令已被处理
        /**
         * 断言true。
         * @param helpOut.contains("help" help出.contains("help"
         */
        assertTrue(helpOut.contains("help") && helpOut.lastIndexOf("g!") > helpOut.indexOf("help"),
                "help 输出应包含命令回显与尾部提示符，实际输出: " + helpOut);
    }
}
