package com.chua.starter.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * osgi 真实测试（Telnet 回退模式）：{@code plugin.osgi.shell.protocol=telnet} 独立上下文。
 *
 * <p>验证链路：Felix 框架正常启动（SSH bean 退避为 null）；telnet 远程管理声明但不具备
 * 框架级监听能力（Gogo 1.1.4 仅提供 {@code telnetd PORT start} 会话内命令，无
 * {@code osgi.shell.telnet.*} 配置监听），默认端口 6666 应无任何监听——此为真实存在的
 * 设计缺口，本测试如实记录：telnet 模式当前仅完成配置透传，不实际监听。</p>
 *
 * @author CH
 * @since 2026/09/11
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=true",
        "plugin.osgi.storage=target/osgi-cache-telnet-test",
        "plugin.osgi.shell.enabled=true",
        "plugin.osgi.shell.protocol=telnet",
        "plugin.osgi.shell.port=6666",
        "plugin.osgi.shell.host=127.0.0.1"
})
/**
 * osgitelnet降级real测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class OsgiTelnetFallbackRealTest {

    @Autowired
    private ObjectProvider<com.chua.starter.osgi.shell.GogoSshShellServer> sshShellProvider; // sshshell提供者

    @Autowired
    private ObjectProvider<com.chua.osgi.support.FelixOsgiLauncher> osgiLauncherProvider; // osgilauncher提供者

    /**
     * 真实测试：telnet 模式下 SSH Shell Bean 应退避为 空（协议二选一）
    */
    @Test
    void sshShellBackedOffInTelnetMode() {
        /**
         * 断言空。
         * @param sshShellProvider.getIfAvailable( sshshell提供者.获取if可用(
         */
        assertNull(sshShellProvider.getIfAvailable(),
                "protocol=telnet 时 GogoSshShellServer 不应装配");
    }

    /**
     * 真实测试：telnet 模式下 Felix 框架应正常启动（含 Gogo bundle 套件）
    */
    @Test
    void felixStartsInTelnetMode() {
        com.chua.osgi.support.FelixOsgiLauncher launcher = osgiLauncherProvider.getIfAvailable();
        /**
         * 断言true。
         * @param launcher.isActive( launcher.是否活跃(
         */
        assertTrue(launcher != null && launcher.isActive(), "telnet 模式下 Felix 框架应激活");
    }

    /**
     * 真实测试：telnet 模式默认端口 6666 无监听（记录端口未被占用设计缺口）
    */
    @Test
    void telnetPortHasNoListener() {
        // 127.0.0.1:6666 尝试连接：预期拒绝（Connection refused）或超时；
        // 如实际被监听，则 telnet 模式真实可用，测试据实判断
        boolean connectable;
        /**
         * 尝试。
         * @param Socket( 套接字(
         */
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6666), 2000);
            connectable = true;
        } catch (IOException e) {
            connectable = false;
        }
        assertTrue(!connectable, "telnet 模式当前无框架级 telnet 监听实现，6666 应无监听（连接应被拒绝），"
                + "若未来补齐监听则此断言需改为 connectable==true");
    }
}