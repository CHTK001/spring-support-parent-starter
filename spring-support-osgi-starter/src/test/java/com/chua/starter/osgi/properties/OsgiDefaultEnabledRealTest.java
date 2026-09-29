package com.chua.starter.osgi.properties;

import com.chua.starter.osgi.shell.GogoSshShellServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「默认配置全开」这一决策的<b>边界实测</b>。
 *
 * <p>把 starter 里所有 enabled 类开关默认打开时，{@code shell} 不能一起开：
 * 默认用户名与密码均为空，而 {@link GogoSshShellServer} 在空凭据且
 * {@code allowEmptyCredential=false} 时<b>抛 IllegalStateException</b>，
 * 自动配置里只 catch 了 IOException，该异常会穿透成启动失败——
 * 即「引入这个 starter 的应用全部起不来」。</p>
 *
 * <p>本测试把该行为固化为断言：若将来有人给空凭据加上兜底（例如改为仅拒绝登录
 * 而非拒绝启动），这里会失败并提示重新评估 shell 的默认值。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class OsgiDefaultEnabledRealTest {

    /**
     * 源码扩展点热重载默认开启（2026-09-29 用户裁定：starter 默认配置都是开启的）。
     */
    @Test
    @DisplayName("源码扩展点默认开启")
    void source_shouldBeEnabledByDefault() {
        var properties = new OsgiProperties();
        assertTrue(properties.getSource().isEnabled(), "源码扩展点应默认开启");
        assertTrue(properties.isEnabled(), "osgi 集成本身默认开启");
    }

    /**
     * shell 默认<b>不能</b>开启，否则空凭据会让应用启动失败。
     */
    @Test
    @DisplayName("shell 默认关闭：空凭据会导致启动失败")
    void shell_shouldStayDisabledByDefault() {
        var properties = new OsgiProperties();
        assertEquals(false, properties.getShell().isEnabled(),
                "shell 默认必须关闭，否则空凭据会抛异常导致启动失败");
        assertEquals("", properties.getShell().getUsername(), "默认无用户名");
        assertEquals("", properties.getShell().getPassword(), "默认无密码");
        assertEquals(false, properties.getShell().isAllowEmptyCredential(),
                "默认禁止空凭据");
    }

    /**
     * 直接验证「空凭据 + 开启 shell = 启动抛异常」这一事实，而非只断言配置默认值。
     *
     * <p>异常在 {@code start()} 而非构造器里抛；而自动配置里
     * {@code gogoSshShellServer} 只 catch 了 {@code IOException}，
     * {@code IllegalStateException} 会穿透成 Bean 创建失败 → 应用启动失败。</p>
     */
    @Test
    @DisplayName("空凭据时 start() 抛异常，证实 shell 不能默认开启")
    void sshShellWithEmptyCredential_shouldThrowOnStart() {
        var shell = new OsgiProperties.Shell();
        assertTrue(shell.getUsername().isEmpty() && shell.getPassword().isEmpty(),
                "前置条件：默认凭据为空");

        var server = new GogoSshShellServer(shell.getHost(), shell.getPort(),
                shell.getUsername(), shell.getPassword(), null, shell.getHostKey(),
                shell.isAllowEmptyCredential());

        var thrown = assertThrows(IllegalStateException.class, server::start,
                "空凭据且未允许时 start() 应抛异常——这正是 shell 不能默认开启的原因");
        assertTrue(thrown.getMessage().contains("allow-empty-credential"),
                "异常信息应指引显式开启空凭据，实际: " + thrown.getMessage());
    }
}
