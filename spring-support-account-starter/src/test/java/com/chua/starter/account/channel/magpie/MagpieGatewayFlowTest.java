// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.ai.chat.route.RouteTarget;
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
 * Magpie 渠道的端到端流程测试（绑定 → 探活 → 模型 → 路由 → 配额 → 签到）。
 *
 * <p>真实 magpie 需要桌面端 / Docker，CI 里没有，所以这里用
 * {@link FakeMagpieServer} 同时扮演数据面（{@code /v1/*}）与管理面（{@code /api/*}），
 * 验证「两个面、两套鉴权」的接线没有接错端口、没有混用凭据。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
class MagpieGatewayFlowTest {

    /**
     * 假 magpie。
     */
    private FakeMagpieServer server;

    /**
     * 被测鉴权实现。
     */
    private final MagpieAccountChannelAuthManager auth = new MagpieAccountChannelAuthManager();

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
    @DisplayName("粘贴整条启动链接即可绑定：?k= 拆成页面 Key，数据面留在网关端口")
    void bindFromFullLink() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("baseUrl", server.origin() + "/?k=" + FakeMagpieServer.PAGE_KEY);

        Map<String, Object> credentials = auth.pollLogin(handle);

        assertEquals(server.origin(), credentials.get("base_url"));
        assertEquals(server.origin(), credentials.get("page_url"));
        assertEquals(FakeMagpieServer.PAGE_KEY, credentials.get("page_key"));
        assertEquals("Magpie ****6789", credentials.get("nickname"));

        Map<String, Object> status = auth.status(credentials);
        assertEquals("valid", status.get("status"));
    }

    @Test
    @DisplayName("网关不可达时绑定失败并带上地址，不落半截凭据")
    void bindRejectsUnreachableGateway() {
        Map<String, Object> handle = new LinkedHashMap<>();
        handle.put("baseUrl", "http://127.0.0.1:1");

        ChannelException error = assertThrows(ChannelException.class, () -> auth.pollLogin(handle));
        assertTrue(error.getMessage().contains("探活失败"));
        assertTrue(error.getMessage().contains("127.0.0.1:1"));
    }

    @Test
    @DisplayName("模型清单归一成 {id,name,ownedBy}")
    void listModelsNormalizes() {
        List<Map<String, Object>> models = auth.listModels(credentials());

        assertEquals(1, models.size());
        assertEquals("codex/gpt-5", models.get(0).get("id"));
        assertEquals("codex", models.get(0).get("ownedBy"));
    }

    @Test
    @DisplayName("模型读取失败只返回空列表，不把渠道探活打红")
    void listModelsDegradesToEmpty() {
        Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("base_url", "http://127.0.0.1:1");
        assertTrue(auth.listModels(broken).isEmpty());
    }

    @Test
    @DisplayName("路由目标是 OpenAI 兼容的 /v1，空 key 用本机占位值")
    void routeTargetPointsToV1() {
        RouteTarget target = auth.toRouteTarget(credentials());

        assertEquals("openai", target.getProvider());
        assertEquals(server.origin() + "/v1", target.getBaseUrl());
        assertEquals(MagpieSupport.LOCAL_KEY, target.getApiKey());
    }

    @Test
    @DisplayName("配额按单位分桶：货币余额不跟 token 窗口相加")
    void creditBucketsByUnit() {
        Map<String, Object> credit = new MagpieChannelCreditProvider().credit(credentials());

        assertEquals(12L, ((Number) credit.get("remain")).longValue());
        assertEquals("currency", credit.get("unit"));
        assertEquals("pro", credit.get("plan"));
        List<?> packages = (List<?>) credit.get("packages");
        assertEquals(2, packages.size());
        String warning = String.valueOf(credit.get("warning"));
        assertTrue(warning.contains("单位不一致"));
    }

    @Test
    @DisplayName("签到聚合四家上游：有账号的记结果，没账号的不算失败")
    void checkinAggregatesVendors() {
        Map<String, Object> updated = auth.checkin(credentials());

        String message = String.valueOf(updated.get("checkin_message"));
        assertTrue(message.contains("workbuddy 处理 1 个账号"));
        assertFalse(message.contains("trae"), "没有账号的上游不该出现在摘要里");
        assertTrue(updated.get("checkin_at") instanceof Long at && at > 0);
        assertEquals(1, server.hits("/api/usage/workbuddy-checkin"));
        assertEquals(1, server.hits("/api/usage/minimax-checkin"));
        assertEquals(1, server.hits("/api/usage/qoder-checkin"));
    }

    @Test
    @DisplayName("页面端口没开时签到失败，提示去打开「浏览器访问」")
    void checkinExplainsClosedPage() {
        Map<String, Object> closed = new LinkedHashMap<>();
        closed.put("base_url", server.origin());
        closed.put("page_url", "http://127.0.0.1:1");
        closed.put("page_key", FakeMagpieServer.PAGE_KEY);

        ChannelException error = assertThrows(ChannelException.class, () -> auth.checkin(closed));
        assertTrue(error.getMessage().contains("浏览器访问"));
    }

    /**
     * 与管理面共用一个假服务的凭据。
     *
     * @return 凭据
     */
    private Map<String, Object> credentials() {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("base_url", server.origin());
        credentials.put("page_url", server.origin());
        credentials.put("page_key", FakeMagpieServer.PAGE_KEY);
        return credentials;
    }
}
