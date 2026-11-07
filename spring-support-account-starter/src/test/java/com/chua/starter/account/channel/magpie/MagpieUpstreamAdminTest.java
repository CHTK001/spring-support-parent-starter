// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.ChannelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MagpieUpstreamAdmin} 的插件 / 登录 / 额度契约测试。
 *
 * <p>覆盖这次「渠道数据交给 magpie 管」改造里最容易回归的几段：</p>
 * <ul>
 *   <li>插件清单归一化与动作白名单；</li>
 *   <li>插件登录与内置登录走不同端点；</li>
 *   <li>额度按 provider / 账号过滤；</li>
 *   <li>签到按 {@code move.state=plugin} 分流，且自动签到不再重复跑。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
class MagpieUpstreamAdminTest {

    /**
     * 假 magpie。
     */
    private FakeMagpieServer server;

    /**
     * 被测实现。
     */
    private final MagpieUpstreamAdmin admin = new MagpieUpstreamAdmin();

    @BeforeEach
    void startServer() throws IOException {
        server = new FakeMagpieServer();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("上游列表带出插件接管状态、插件账号数，并保持凭据脱敏")
    void listExposesPluginState() {
        server.takeoverWorkbuddy();
        List<?> providers = (List<?>) admin.list(credentials()).get("providers");
        Map<?, ?> workbuddy = providers.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<?, ?>) item)
                .filter(p -> "workbuddy".equals(p.get("id")))
                .findFirst()
                .orElseThrow();

        assertEquals("plugin", ((Map<?, ?>) workbuddy.get("move")).get("state"));
        assertEquals(2, workbuddy.get("pluginAccounts"));
        // 内置那一行的明文 key 必须脱敏
        Map<?, ?> codex = providers.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<?, ?>) item)
                .filter(p -> "codex".equals(p.get("id")))
                .findFirst()
                .orElseThrow();
        assertFalse(String.valueOf(codex.get("key")).contains("secret"));
    }

    @Test
    @DisplayName("插件与登录能力位打开，前端据此显示页签")
    void pluginAndSigninCapabilities() {
        assertTrue(admin.supportsPlugins());
        assertTrue(admin.supportsSignin());
    }

    @Test
    @DisplayName("插件清单归一成 spec/name/off/version，市场单独给 listings")
    void pluginsAreNormalized() {
        Map<String, Object> state = admin.plugins(credentials(), false);
        List<?> plugins = (List<?>) state.get("plugins");
        assertEquals(1, plugins.size());
        Map<?, ?> plugin = (Map<?, ?>) plugins.get(0);
        assertEquals("@magpie-community/opencode-workbuddy-auth", plugin.get("spec"));
        assertEquals("0.2.24", plugin.get("version"));
        assertEquals(Boolean.FALSE, plugin.get("off"));
        assertEquals(List.of("workbuddy"), plugin.get("moved"));
        assertEquals(Boolean.TRUE, state.get("bun"));

        Map<String, Object> withMarket = admin.plugins(credentials(), true);
        assertNotNull(withMarket.get("listings"));
        assertEquals(1, ((List<?>) withMarket.get("listings")).size());
    }

    @Test
    @DisplayName("插件动作白名单之外直接拒绝，不打上游")
    void pluginActionRejectsUnknownOp() {
        ChannelException error = assertThrows(ChannelException.class,
                () -> admin.pluginAction(credentials(), "reboot", Map.of()));
        assertEquals(ChannelException.CODE_INVALID_PARAM, error.getCode());
        assertEquals(0, server.hits("/api/plugins/reboot"));
    }

    @Test
    @DisplayName("安装插件把 spec 透传给 /api/plugins/add")
    void pluginAddPostsSpec() {
        admin.pluginAction(credentials(), "add",
                Map.of("spec", "@magpie-community/opencode-workbuddy-auth", "connectionId", "c1"));

        assertEquals(1, server.hits("/api/plugins/add"));
        assertTrue(server.lastBody().contains("opencode-workbuddy-auth"));
        assertFalse(server.lastBody().contains("connectionId"), "控制字段不应透传给 magpie");
    }

    @Test
    @DisplayName("带 provider 走插件登录，带 agent 走内置登录")
    void signinRoutesToPluginOrBuiltin() {
        Map<String, Object> pluginSession = admin.startSignin(credentials(),
                Map.of("provider", "workbuddy", "method", 0));
        assertEquals("s1", pluginSession.get("id"));
        assertEquals(1, server.hits("/api/plugin-signin"));

        Map<String, Object> builtinSession = admin.startSignin(credentials(),
                Map.of("agent", "codex"));
        assertEquals("s1", builtinSession.get("id"));
        assertEquals(1, server.hits("/api/signin"));
        assertFalse(server.lastBody().contains("plugin"), "control 字段不应透传");
    }

    @Test
    @DisplayName("登录状态轮询与取消走 /api/signin/{id}")
    void signinStatusAndCancel() {
        Map<String, Object> status = admin.signinStatus(credentials(), "s1");
        assertEquals("done", status.get("state"));
        assertEquals("alice", status.get("user"));

        admin.cancelSignin(credentials(), "s1");
        assertEquals(1, server.hits("/api/signin/s1/cancel"));
    }

    @Test
    @DisplayName("额度按 provider / 账号过滤")
    void quotasFilterByProviderAndUser() {
        server.withAdminQuotas();
        Map<String, Object> all = admin.quotas(credentials(), null, null);
        assertEquals(1, ((List<?>) all.get("quotas")).size());

        Map<String, Object> matched = admin.quotas(credentials(), "workbuddy", "alice");
        assertEquals(1, ((List<?>) matched.get("quotas")).size());

        Map<String, Object> other = admin.quotas(credentials(), "codex", null);
        assertTrue(((List<?>) other.get("quotas")).isEmpty());
    }

    @Test
    @DisplayName("配了页面 Key 时积分优先读管理面额度卡，读不到再回落数据面")
    void creditPrefersAdminQuotas() {
        server.withAdminQuotas();
        Map<String, Object> credit = new MagpieChannelCreditProvider().credit(credentials());
        assertEquals(10L, ((Number) credit.get("remain")).longValue());
        assertEquals(1, server.hits("/api/usage/quotas"));

        Map<String, Object> fallback = new MagpieChannelCreditProvider()
                .credit(credentials("", ""));
        assertEquals(12L, ((Number) fallback.get("remain")).longValue());
    }

    @Test
    @DisplayName("刷新额度必须给 provider")
    void refreshQuotaRequiresProvider() {
        ChannelException error = assertThrows(ChannelException.class,
                () -> admin.refreshQuota(credentials(), "  ", null));
        assertEquals(ChannelException.CODE_INVALID_PARAM, error.getCode());

        Map<String, Object> refreshed = admin.refreshQuota(credentials(), "workbuddy", "alice");
        assertEquals(1, ((List<?>) refreshed.get("quotas")).size());
        assertEquals(1, server.hits("/api/usage/quotas/refresh"));
    }

    @Test
    @DisplayName("WorkBuddy 被插件接管后，签到改走 plugin-checkin")
    void checkinUsesPluginEndpointWhenTakenOver() {
        server.takeoverWorkbuddy();
        MagpieAccountChannelAuthManager manager = new MagpieAccountChannelAuthManager();

        Map<String, Object> after = manager.checkin(credentials());

        assertEquals(1, server.hits("/api/usage/plugin-checkin"));
        assertEquals(0, server.hits("/api/usage/workbuddy-checkin"),
                "被插件接管后不能再打内置 workbuddy 端点");
        String message = String.valueOf(after.get("checkin_message"));
        assertTrue(message.contains("workbuddy（插件）"), message);
        assertNotNull(after.get("checkin_at"));
    }

    @Test
    @DisplayName("未被接管时仍走内置端点；自动签到不接管 magpie")
    void checkinFallsBackToBuiltinAndSkipsAuto() {
        MagpieAccountChannelAuthManager manager = new MagpieAccountChannelAuthManager();

        manager.checkin(credentials());

        assertEquals(1, server.hits("/api/usage/workbuddy-checkin"));
        assertEquals(0, server.hits("/api/usage/plugin-checkin"));
        assertTrue(manager.supportsCheckin());
        assertFalse(manager.supportsAutoCheckin(), "magpie 自带签到定时器，自动任务应跳过");
    }

    /**
     * 构造连接凭据（数据面与管理面都指向假服务）。
     *
     * @return 凭据
     */
    private Map<String, Object> credentials() {
        return credentials(FakeMagpieServer.PAGE_KEY, server.origin());
    }

    /**
     * 构造指定页面 Key 的凭据（页面地址为空时仍指向假服务）。
     *
     * @param pageKey 页面 Key（空表示不配管理面）
     * @param pageUrl 页面地址（空表示用假服务地址）
     * @return 凭据
     */
    private Map<String, Object> credentials(String pageKey, String pageUrl) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("base_url", server.origin());
        credentials.put("page_url", pageUrl == null || pageUrl.isBlank() ? server.origin() : pageUrl);
        credentials.put("page_key", pageKey);
        return credentials;
    }
}
