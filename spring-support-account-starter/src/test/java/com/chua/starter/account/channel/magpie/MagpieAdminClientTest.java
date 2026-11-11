// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.channel.MagpieSupport;
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
 * {@link MagpieAdminClient} 的接口契约测试。
 *
 * <p>用 {@link FakeMagpieServer} 覆盖真实交互里最容易出错、又最不容易在本地
 * 复现的几段：</p>
 * <ul>
 *   <li><b>页面 Cookie 握手</b>：{@code GET /?k=…} → {@code 303 + Set-Cookie}，
 *       之后 {@code /api/*} 必须带该 Cookie；</li>
 *   <li><b>管理面形状</b>：{@code models} 是对象数组、凭据在 {@code keyList}；</li>
 *   <li><b>动作分发</b>：{@code save}（含 {@code new}/{@code from}）、{@code delete}、
 *       {@code models} 的请求体与响应；</li>
 *   <li><b>签到聚合</b>：有账号的上游返回行，没有账号的返回空数组；</li>
 *   <li><b>可操作报错</b>：页面 Key 缺失 / 被拒时给出中文指引，而不是裸 401。</li>
 * </ul>
 *
 * @since 4.0.0.53
 * @author CH
 */
class MagpieAdminClientTest {

    /**
     * 假 magpie。
     */
    private FakeMagpieServer server;

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
    @DisplayName("管理面先换 Cookie，再带 Cookie 读 providers")
    void providersHandshakesCookieThenReads() {
        Map<String, Object> credentials = credentials(FakeMagpieServer.PAGE_KEY);

        List<Map<String, Object>> rows = MagpieAdminClient.providers(credentials);

        assertEquals(1, rows.size());
        assertEquals("codex", rows.get(0).get("id"));
        assertTrue(server.hits("/") > 0, "必须先发生 ?k= → Set-Cookie 握手");
        assertTrue(server.hits("/api/providers") > 0);
        assertTrue(MagpieAdminClient.manageable(credentials));
    }

    @Test
    @DisplayName("缺页面 Key 时报中文指引，而不是空指针或裸 401")
    void missingPageKeyGivesActionableHint() {
        Map<String, Object> credentials = credentials("");
        ChannelException error = assertThrows(ChannelException.class,
                () -> MagpieAdminClient.providers(credentials));
        assertTrue(error.getMessage().contains("页面 Key"));
        assertEquals(0, server.hits("/api/providers"), "没有页面 Key 不该打管理面");
    }

    @Test
    @DisplayName("页面 Key 被拒时提示重新复制启动链接")
    void rejectedPageKeyGivesActionableHint() {
        Map<String, Object> credentials = credentials("wrong-key-000000");
        ChannelException error = assertThrows(ChannelException.class,
                () -> MagpieAdminClient.providers(credentials));
        assertTrue(error.getMessage().contains("页面 Key 被拒"));
    }

    @Test
    @DisplayName("Cookie 被上游作废（magpie 重启/轮换）时自动重握手，不必等 30 分钟缓存过期")
    void staleCookieIsRekeyedOnce() {
        Map<String, Object> credentials = credentials(FakeMagpieServer.PAGE_KEY);
        assertEquals(1, MagpieAdminClient.providers(credentials).size());
        int handshakes = server.hits("/");

        server.rotateCookie();

        List<Map<String, Object>> rows = MagpieAdminClient.providers(credentials);
        assertEquals(1, rows.size(), "重握手后应拿到数据，而不是把 401 抛给用户");
        assertEquals(handshakes + 1, server.hits("/"), "只重握手一次");
        // 首次成功 1 次 + 失效那次 401 + 重试成功 1 次
        assertEquals(3, server.hits("/api/providers"));
    }

    @Test
    @DisplayName("页面 Key 本身不对时不无限重试：握手两次就报错")
    void wrongKeyDoesNotRetryForever() {
        Map<String, Object> credentials = credentials("wrong-key-000000");
        ChannelException error = assertThrows(ChannelException.class,
                () -> MagpieAdminClient.providers(credentials));
        assertTrue(error.getMessage().contains("页面 Key 被拒"));
        assertEquals(2, server.hits("/"), "首次握手 + 一次重试，不再继续打上游");
    }

    @Test
    @DisplayName("save 原样透传 provider 字段与 new/from 标记")
    void savePostsProviderBody() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("id", "codex");
        provider.put("name", "Codex 订阅");
        provider.put("new", true);
        provider.put("from", "codex-old");
        provider.put("key", "sk-upstream");

        Map<String, Object> result = MagpieAdminClient.save(credentials(FakeMagpieServer.PAGE_KEY), provider);

        assertNotNull(result);
        String body = server.lastBody();
        assertTrue(body.contains("\"id\":\"codex\""));
        assertTrue(body.contains("\"new\":true"));
        assertTrue(body.contains("\"from\":\"codex-old\""));
    }

    @Test
    @DisplayName("delete 只发 id")
    void deletePostsId() {
        MagpieAdminClient.delete(credentials(FakeMagpieServer.PAGE_KEY), "claude");

        String body = server.lastBody();
        assertTrue(body.contains("\"id\":\"claude\""));
        assertEquals(1, server.hits("/api/provider/delete"));
    }

    @Test
    @DisplayName("models 是对象数组，归一成 id 列表")
    void modelsAreNormalizedFromObjects() {
        List<String> ids = MagpieAdminClient.providerModelIds(credentials(FakeMagpieServer.PAGE_KEY),
                Map.of("id", "codex"));

        assertEquals(List.of("alpha", "beta"), ids);
    }

    @Test
    @DisplayName("签到按上游逐家调用：有账号返回行，没账号返回空")
    void checkinCallsEachVendor() {
        Map<String, Object> credentials = credentials(FakeMagpieServer.PAGE_KEY);

        List<Map<String, Object>> workbuddy = MagpieAdminClient.checkin(credentials, "workbuddy");
        List<Map<String, Object>> trae = MagpieAdminClient.checkin(credentials, "trae");

        assertEquals(1, workbuddy.size());
        assertEquals("a1", workbuddy.get(0).get("account"));
        assertTrue(trae.isEmpty());
        assertEquals(1, server.hits("/api/usage/workbuddy-checkin"));
        assertEquals(1, server.hits("/api/usage/trae-checkin"));
    }

    @Test
    @DisplayName("配额读数来自数据面 /v1/magpie/quotas，空 key 用本机占位值")
    void quotasReadFromDataPlane() {
        List<Map<String, Object>> rows = MagpieAdminClient.quotas(server.origin(), "");

        assertEquals(2, rows.size());
        assertEquals("codex", rows.get(0).get("provider"));
        assertEquals("Bearer " + MagpieSupport.LOCAL_KEY, server.dataPlaneAuthorization());
        assertFalse(server.hits("/api/providers") > 0, "数据面不该碰管理面");
    }

    /**
     * 构造连接凭据（数据面与管理面都指向假服务）。
     *
     * @param pageKey 页面 Key
     * @return 凭据
     */
    private Map<String, Object> credentials(String pageKey) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("base_url", server.origin());
        credentials.put("page_url", server.origin());
        credentials.put("page_key", pageKey);
        return credentials;
    }
}
