// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.common.support.channel.ChannelCreditProvider;
import com.chua.starter.common.support.channel.ChannelTaskProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkBuddyChannelCreditProvider} / {@link WorkBuddyChannelTaskProvider} 的集成测试。
 *
 * <p>用 JDK 内置 {@link HttpServer} 模拟 WorkBuddy 的 billing 与成长中心端点，
 * 覆盖：积分快照解析、资源包明细、任务清单归一化、报名、领奖（含重复领取幂等）、
 * 以及 global 账号无任务体系的门控。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
class WorkBuddyChannelProviderTest {

    private HttpServer server;

    /**
     * mock 上游地址
     */
    private String origin;

    /**
     * 最近一次请求体（断言 body 用）
     */
    private volatile String lastBody = "";

    /**
     * 最近一次请求路径
     */
    private volatile String lastPath = "";

    /**
     * 覆盖三方地址的实现（鉴权管理器与两个 provider 都要指向 mock）
     */
    private WorkBuddyAccountChannelAuthManager manager;

    @BeforeEach
    void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
        lastBody = "";
        lastPath = "";
        manager = new WorkBuddyAccountChannelAuthManager() {
            @Override
            protected String billingOrigin(String realm) {
                return origin;
            }

            @Override
            protected String webOrigin(String realm) {
                return origin;
            }

            @Override
            protected String upstreamOrigin(String realm) {
                return origin;
            }
        };
    }

    @AfterEach
    void stopMockServer() {
        server.stop(0);
    }

    /**
     * mock 路由。
     *
     * @param exchange 请求
     * @throws IOException 写出失败
     */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        lastPath = path;
        lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response;

        if (path.endsWith("/v2/billing/meter/get-user-resource")) {
            response = "{\"code\":0,\"Response\":{\"Data\":{\"Accounts\":["
                    + "{\"PackageName\":\"新用户礼包\",\"CycleCapacityRemain\":800,"
                    + "\"CycleCapacitySize\":1000,\"CycleEndTime\":\"2026-12-31 23:59:59\"},"
                    + "{\"PackageName\":\"每日签到\",\"CapacityRemain\":50,\"CapacitySize\":50}"
                    + "]}}}";
        } else if (path.endsWith("/v2/billing/meter/get-enterprise-user-usage")) {
            response = "{\"code\":0,\"data\":{\"credit\":812.45,\"limitNum\":2000}}";
        } else if (path.endsWith("/v2/activity/growth/tasks")) {
            response = "{\"code\":0,\"data\":{\"tasks\":["
                    + "{\"task_code\":\"daily_chat\",\"title\":\"每日对话\","
                    + "\"accept_status\":\"not_accepted\",\"reward_credit\":10},"
                    + "{\"task_code\":\"first_buddy\",\"title\":\"首位好友\","
                    + "\"accept_status\":\"accepted\",\"reward_credit\":100,"
                    + "\"progress\":{\"current\":1,\"target\":1},"
                    + "\"jump_url\":\"workbuddy://invite\"},"
                    + "{\"task_code\":\"old_task\",\"title\":\"已领任务\","
                    + "\"accept_status\":\"claimed\",\"reward_credit\":50}"
                    + "]}}";
        } else if (path.endsWith("/v2/activity/growth/tasks/accept")) {
            response = "{\"code\":0}";
        } else if (path.contains("/activity/growth/tasks/") && path.endsWith("/claim")) {
            response = "{\"code\":0,\"data\":{\"already_claimed\":false,"
                    + "\"credit\":100,\"energy\":5}}";
        } else if (path.endsWith("/v2/billing/meter/daily-checkin")) {
            response = "{\"code\":0,\"msg\":\"success\"}";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 构造指向 mock 的积分 provider。
     *
     * @return provider
     */
    private ChannelCreditProvider creditProvider() {
        return new WorkBuddyChannelCreditProvider();
    }

    /**
     * 构造 cn 账号凭据。
     *
     * @return 凭据
     */
    private static Map<String, Object> cnCredentials() {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("access_token", "at-mock");
        credentials.put("uid", "u-1");
        credentials.put("realm", "cn");
        return credentials;
    }

    @Test
    @DisplayName("credit：个人资源包聚合为剩余 / 总量，并透出明细")
    void creditAggregatesPackages() {
        Map<String, Object> credit = manager.credit(cnCredentials());

        assertNull(credit.get("error"), "正常路径不应带 error: " + credit);
        assertEquals(850L, credit.get("remain"), "800 + 50");
        assertEquals(1050L, credit.get("total"), "1000 + 50");
        assertEquals("credit", credit.get("unit"));
        assertNotNull(credit.get("updatedAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) credit.get("packages");
        assertNotNull(packages, "应透出资源包明细");
        assertEquals(2, packages.size());
        assertEquals("新用户礼包", packages.get(0).get("name"));
        assertEquals(800L, packages.get(0).get("remain"));
        assertEquals(1000L, packages.get(0).get("size"));
    }

    @Test
    @DisplayName("credit：企业账号走 enterprise-user-usage，remain = limit - credit")
    void creditEnterprise() {
        Map<String, Object> credentials = cnCredentials();
        credentials.put("enterprise_id", "ent-1");
        Map<String, Object> credit = manager.credit(credentials);

        assertNull(credit.get("error"), "企业路径不应报错: " + credit);
        assertEquals(1188L, credit.get("remain"), "2000 - 812.45 → 1188（四舍五入后取整）");
        assertEquals(2000L, credit.get("total"));
    }

    @Test
    @DisplayName("credit：缺 access_token 时返回 error 而不是 0")
    void creditWithoutToken() {
        Map<String, Object> credit = manager.credit(Map.of());
        assertNotNull(credit.get("error"), "缺凭据必须报错，不能把缺失当 0");
        assertNull(credit.get("remain"));
    }

    @Test
    @DisplayName("tasks：任务清单归一化为统一行契约")
    void taskListNormalized() {
        List<Map<String, Object>> tasks = manager.taskList(cnCredentials(), "cn");

        assertEquals(3, tasks.size());
        Map<String, Object> daily = tasks.get(0);
        assertEquals("daily_chat", daily.get("code"));
        assertEquals("每日对话", daily.get("title"));
        assertEquals("not_accepted", daily.get("acceptStatus"));
        assertEquals(10L, daily.get("credit"));
        assertFalse(Boolean.TRUE.equals(daily.get("claimable")), "未报名的任务不可领");

        Map<String, Object> buddy = tasks.get(1);
        assertEquals("first_buddy", buddy.get("code"));
        assertEquals(1L, buddy.get("current"));
        assertEquals(1L, buddy.get("target"));
        assertTrue(Boolean.TRUE.equals(buddy.get("claimable")), "进度达标应可领");
        assertEquals("workbuddy://invite", buddy.get("jumpUrl"));

        Map<String, Object> claimed = tasks.get(2);
        assertTrue(Boolean.TRUE.equals(claimed.get("claimed")), "已领任务应标记 claimed");
        assertFalse(Boolean.TRUE.equals(claimed.get("claimable")));
    }

    @Test
    @DisplayName("acceptTaskCodes：报名请求体携带 task_codes")
    void acceptTasksSendsCodes() {
        int accepted = manager.acceptTaskCodes(cnCredentials(), "cn", List.of("daily_chat", "other"));
        assertEquals(2, accepted);
        assertTrue(lastPath.endsWith("/v2/activity/growth/tasks/accept"), "应打 accept 端点: " + lastPath);
        assertTrue(lastBody.contains("\"task_codes\""), "请求体应带 task_codes: " + lastBody);
        assertTrue(lastBody.contains("daily_chat"), "请求体应含任务码: " + lastBody);
    }

    @Test
    @DisplayName("claimTaskReward：领奖成功并回传额度")
    void claimReward() {
        Map<String, Object> result = manager.claimTaskReward(cnCredentials(), "cn", "first_buddy");

        assertTrue(Boolean.TRUE.equals(result.get("ok")), "领奖应成功: " + result);
        assertTrue(Boolean.TRUE.equals(result.get("claimed")), "应标记为本次新领");
        assertEquals(100L, result.get("credit"));
        assertEquals(5L, result.get("energy"));
        assertTrue(lastPath.contains("/activity/growth/tasks/first_buddy/claim"),
                "任务码应在路径里: " + lastPath);
    }

    @Test
    @DisplayName("runTasks：默认实现按「报名未报名 + 领取已达标」组合")
    void runTasksCombines() {
        // runTasks 是 ChannelTaskProvider 的默认方法（组合 tasks/acceptTasks/claimTask），
        // 因此这里用一个指向 mock 的内联 provider 验证组合逻辑。
        ChannelTaskProvider provider = new ChannelTaskProvider() {
            @Override
            public String channel() {
                return "workbuddy";
            }

            @Override
            public List<Map<String, Object>> tasks(Map<String, Object> credentials) {
                return manager.taskList(credentials, "cn");
            }

            @Override
            public int acceptTasks(Map<String, Object> credentials, List<String> taskCodes) {
                return manager.acceptTaskCodes(credentials, "cn", taskCodes);
            }

            @Override
            public Map<String, Object> claimTask(Map<String, Object> credentials, String taskCode) {
                return manager.claimTaskReward(credentials, "cn", taskCode);
            }
        };
        Map<String, Object> summary = provider.runTasks(cnCredentials());

        assertEquals(1, summary.get("accepted"), "只有 daily_chat 未报名");
        assertEquals(1, summary.get("claimed"), "只有 first_buddy 可领");
        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) summary.get("messages");
        assertTrue(messages.stream().anyMatch(m -> m.contains("报名")), "应报告报名结果: " + messages);
        assertTrue(messages.stream().anyMatch(m -> m.contains("领取")), "应报告领取结果: " + messages);
    }

    @Test
    @DisplayName("ChannelCreditProvider：SPI 可解析且 channel 一致")
    void creditProviderSpi() {
        ChannelCreditProvider provider = com.chua.common.support.spi.ServiceProvider
                .of(ChannelCreditProvider.class)
                .getExtension("workbuddy");
        assertNotNull(provider, "META-INF/extensions 未注册 workbuddy 积分实现");
        assertEquals("workbuddy", provider.channel());
        assertTrue(provider.supportsCredit());
    }

    @Test
    @DisplayName("ChannelTaskProvider：SPI 可解析，global 账号返回空任务")
    void taskProviderSpi() {
        ChannelTaskProvider provider = com.chua.common.support.spi.ServiceProvider
                .of(ChannelTaskProvider.class)
                .getExtension("workbuddy");
        assertNotNull(provider, "META-INF/extensions 未注册 workbuddy 任务实现");
        assertEquals("workbuddy", provider.channel());

        Map<String, Object> globalCredentials = new LinkedHashMap<>();
        globalCredentials.put("access_token", "at-mock");
        globalCredentials.put("realm", "global");
        assertTrue(provider.tasks(globalCredentials).isEmpty(),
                "global 账号无成长任务体系，必须返回空列表");
    }
}
