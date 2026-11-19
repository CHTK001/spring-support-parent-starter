package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.support.CreditSigner;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 火山引擎（豆包 / 火山方舟）余额 + 订阅聚合实现验证。
 *
 * <p>解析逻辑全部喂官方文档样例（GetPersonalPlan / GetCodingPlanUsage /
 * GetAFPUsage / ResourceNotFound 错误信封），不发网络请求；签名验证只断言
 * 请求形态（POST、body 摘要头、签名头域清单），签名值正确性由真实链路用例兜底。
 * 真实端点用例走环境变量 {@code VOLC_ACCESS_KEY} / {@code VOLC_SECRET_KEY}
 * （也认同名系统属性），缺失时跳过、绝不 mock。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
class VolcenginePlanCreditQueryProviderTest {

    /**
     * GetPersonalPlan 官方样例（CodingPlan Pro，生效中，自动续费）
     */
    private static final String PLAN_CODING_OK = """
            {
              "ResponseMetadata": {"RequestId": "req-1", "Action": "GetPersonalPlan",
                  "Version": "2024-01-01", "Service": "ark", "Region": "cn-beijing"},
              "Result": {
                "Plan": "CodingPlan",
                "PlanType": "Pro",
                "Status": "Running",
                "StartTime": "2025-06-01T00:00:00Z",
                "EndTime": "2025-07-01T00:00:00Z",
                "AutoRenew": true
              }
            }
            """;

    /**
     * GetPersonalPlan 官方样例（AgentPlan Max）
     */
    private static final String PLAN_AGENT_OK = """
            {
              "ResponseMetadata": {"RequestId": "req-2", "Action": "GetPersonalPlan",
                  "Version": "2024-01-01", "Service": "ark", "Region": "cn-beijing"},
              "Result": {
                "Plan": "AgentPlan",
                "PlanType": "Max",
                "Status": "Running",
                "StartTime": "2025-06-01T00:00:00Z",
                "EndTime": "2025-07-01T00:00:00Z",
                "AutoRenew": false
              }
            }
            """;

    /**
     * 未订阅时上游返回的错误信封（HTTP 非 2xx，CreditHttp 已把错误码并进异常消息；
     * 这里按 200 + 错误信封的形态直喂解析器，覆盖两种到达路径）
     */
    private static final String PLAN_NOT_FOUND = """
            {
              "ResponseMetadata": {
                "RequestId": "req-3",
                "Error": {"Code": "ResourceNotFound.Plan", "Message": "The plan you are looking for does not exist"}
              }
            }
            """;

    /**
     * GetCodingPlanUsage 官方样例：session 窗口 ResetTimestamp=-1（不重置），其余给秒级时间戳
     */
    private static final String USAGE_CODING_OK = """
            {
              "ResponseMetadata": {"RequestId": "req-4", "Action": "GetCodingPlanUsage",
                  "Version": "2024-01-01", "Service": "ark", "Region": "cn-beijing"},
              "Result": {
                "QuotaUsage": [
                  {"Level": "session", "Percent": 10, "ResetTimestamp": -1, "Cap": 100000,
                      "Status": "Normal", "UpdateTimestamp": 1730000000},
                  {"Level": "weekly", "Percent": 35, "ResetTimestamp": 1730500000, "Cap": 500000, "Status": "Normal"},
                  {"Level": "monthly", "Percent": 20, "ResetTimestamp": 1731000000, "Cap": 2000000, "Status": "Normal"}
                ]
              }
            }
            """;

    /**
     * GetAFPUsage 官方样例：四窗口 Quota / Used 绝对值，ResetTime 毫秒
     */
    private static final String USAGE_AGENT_OK = """
            {
              "ResponseMetadata": {"RequestId": "req-5", "Action": "GetAFPUsage",
                  "Version": "2024-01-01", "Service": "ark", "Region": "cn-beijing"},
              "Result": {
                "PlanType": "Max",
                "AFPFiveHour": {"Quota": 1000, "Used": 250, "SubscribeTime": 1729990000000, "ResetTime": 1730006000000},
                "AFPDaily": {"Quota": 5000, "Used": 1000, "SubscribeTime": 1729990000000, "ResetTime": 1730054400000},
                "AFPWeekly": {"Quota": 20000, "Used": 5000, "SubscribeTime": 1729990000000, "ResetTime": 1730572800000},
                "AFPMonthly": {"Quota": 80000, "Used": 20000, "SubscribeTime": 1729990000000, "ResetTime": 1732416000000}
              }
            }
            """;

    /**
     * CodingPlan 套餐样例解析：套餐名 / 类型 / 状态中文化 / 自动续费。
     */
    @Test
    void parseCodingPlanSample() {
        CreditResult result = VolcenginePlanCreditQueryProvider.parsePlan(PLAN_CODING_OK);
        assertTrue(result.isOk(), result.getError());
        Map<String, Object> extra = result.getExtra();
        assertEquals("Coding Plan · Pro", extra.get("plan"));
        assertEquals("Pro", extra.get("type"));
        assertEquals("生效中", extra.get("status"));
        assertEquals("2025-06-01T00:00:00Z", extra.get("start"));
        assertEquals("2025-07-01T00:00:00Z", extra.get("end"));
        assertEquals("自动续费", extra.get("auto_renew"));
        assertEquals("CodingPlan", VolcenginePlanCreditQueryProvider.planKindOf(result));
    }

    /**
     * AgentPlan 套餐样例解析：类型归一为 AgentPlan（决定用量走 GetAFPUsage）。
     */
    @Test
    void parseAgentPlanSample() {
        CreditResult result = VolcenginePlanCreditQueryProvider.parsePlan(PLAN_AGENT_OK);
        assertTrue(result.isOk(), result.getError());
        assertEquals("Agent Plan · Max", result.getExtra().get("plan"));
        assertEquals("不自动续费", result.getExtra().get("auto_renew"));
        assertEquals("AgentPlan", VolcenginePlanCreditQueryProvider.planKindOf(result));
    }

    /**
     * 未订阅错误信封：带 ResourceNotFound 标记返回失败，调用方据此降级「无订阅」。
     */
    @Test
    void parsePlanNotFoundMarksResourceNotFound() {
        CreditResult result = VolcenginePlanCreditQueryProvider.parsePlan(PLAN_NOT_FOUND);
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("ResourceNotFound"), result.getError());
    }

    /**
     * 套餐类型判定：Plan 字段与 PlanType 都能归一。
     */
    @Test
    void planKindOfMapsPlanAndType() {
        assertEquals("CodingPlan", VolcenginePlanCreditQueryProvider.planKindOf("CodingPlan", ""));
        assertEquals("CodingPlan", VolcenginePlanCreditQueryProvider.planKindOf("", "Lite"));
        assertEquals("CodingPlan", VolcenginePlanCreditQueryProvider.planKindOf("", "Pro"));
        assertEquals("AgentPlan", VolcenginePlanCreditQueryProvider.planKindOf("AgentPlan", ""));
        assertEquals("AgentPlan", VolcenginePlanCreditQueryProvider.planKindOf("", "Max"));
        assertEquals("AgentPlan", VolcenginePlanCreditQueryProvider.planKindOf("", "Small"));
        assertEquals("", VolcenginePlanCreditQueryProvider.planKindOf("", ""));
        assertEquals("", VolcenginePlanCreditQueryProvider.planKindOf("", "Unknown"));
    }

    /**
     * CodingPlan 用量样例解析：三窗口、已用百分比换算成剩余占比、session 窗口无重置时间。
     */
    @Test
    void parseCodingUsageConvertsUsedPercentToRemaining() {
        CreditResult result = VolcenginePlanCreditQueryProvider.parseUsage(USAGE_CODING_OK, "CodingPlan");
        assertTrue(result.isOk(), result.getError());
        assertEquals("Coding Plan 用量窗口", result.getExtra().get("pools_title"));
        List<Map<String, Object>> pools = poolsOf(result);
        assertEquals(1, pools.size());
        assertEquals("Coding Plan", pools.get(0).get("name"));
        List<Map<String, Object>> windows = windowsOf(pools.get(0));
        assertEquals(3, windows.size());
        assertEquals("单次会话", windows.get(0).get("label"));
        assertEquals(BigDecimal.valueOf(90), windows.get(0).get("percent"));
        assertNull(windows.get(0).get("reset"), "session 窗口 ResetTimestamp=-1 不应输出 reset");
        assertEquals("每周", windows.get(1).get("label"));
        assertEquals(BigDecimal.valueOf(65), windows.get(1).get("percent"));
        assertEquals(Instant.ofEpochSecond(1730500000).toString(), windows.get(1).get("reset"));
        assertEquals("每月", windows.get(2).get("label"));
        assertEquals(BigDecimal.valueOf(80), windows.get(2).get("percent"));
    }

    /**
     * AgentPlan 用量样例解析：四窗口带 remaining / limit / usage / percent / reset（毫秒转 ISO）。
     */
    @Test
    void parseAfpUsageFillsAbsoluteWindowValues() {
        CreditResult result = VolcenginePlanCreditQueryProvider.parseUsage(USAGE_AGENT_OK, "AgentPlan");
        assertTrue(result.isOk(), result.getError());
        assertEquals("Agent Plan 用量窗口", result.getExtra().get("pools_title"));
        List<Map<String, Object>> pools = poolsOf(result);
        assertEquals(1, pools.size());
        assertEquals("Agent Plan", pools.get(0).get("name"));
        List<Map<String, Object>> windows = windowsOf(pools.get(0));
        assertEquals(4, windows.size());
        assertEquals("每 5 小时", windows.get(0).get("label"));
        assertEquals(BigDecimal.valueOf(1000), windows.get(0).get("limit"));
        assertEquals(BigDecimal.valueOf(250), windows.get(0).get("usage"));
        assertEquals(BigDecimal.valueOf(750), windows.get(0).get("remaining"));
        assertEquals(BigDecimal.valueOf(75), windows.get(0).get("percent"));
        assertEquals(Instant.ofEpochMilli(1730006000000L).toString(), windows.get(0).get("reset"));
        assertEquals("每日", windows.get(1).get("label"));
        assertEquals(BigDecimal.valueOf(4000), windows.get(1).get("remaining"));
        assertEquals("每周", windows.get(2).get("label"));
        assertEquals("每月", windows.get(3).get("label"));
        assertEquals(BigDecimal.valueOf(60000), windows.get(3).get("remaining"));
    }

    /**
     * 三路合并：余额在前，套餐与用量进 extra；无订阅时 pools 缺席但仍算成功。
     */
    @Test
    void mergePutsBalanceFirstAndKeepsNoPlanNonFatal() {
        CreditResult balance = CreditResult.ok("registry", List.of(part("可用余额", "100.50"), part("现金余额", "30.00")), "{}");
        CreditResult plan = VolcenginePlanCreditQueryProvider.parsePlan(PLAN_CODING_OK);
        CreditResult usage = VolcenginePlanCreditQueryProvider.parseUsage(USAGE_CODING_OK, "CodingPlan");
        CreditResult merged = VolcenginePlanCreditQueryProvider.merge(balance, plan, usage);
        assertTrue(merged.isOk());
        assertEquals("volc-plan", merged.getSource());
        assertEquals(2, merged.getParts().size());
        assertEquals("可用余额", merged.getParts().get(0).getLabel());
        assertEquals(new BigDecimal("100.50"), merged.getParts().get(0).getAmount());
        assertEquals("Coding Plan · Pro", merged.getExtra().get("plan"));
        assertEquals("volc-plan", merged.getExtra().get("mode"));
        assertNotNull(merged.getExtra().get("pools"));

        CreditResult noPlan = VolcenginePlanCreditQueryProvider.merge(balance,
                CreditResult.fail(VolcenginePlanCreditQueryProvider.NAME, VolcenginePlanCreditQueryProvider.NO_PLAN_TEXT), null);
        assertTrue(noPlan.isOk(), "只订余额的用户不该被订阅查询判死");
        assertEquals(2, noPlan.getParts().size());
        assertEquals(VolcenginePlanCreditQueryProvider.NO_PLAN_TEXT, noPlan.getExtra().get("plan"));
        assertNull(noPlan.getExtra().get("pools"));
    }

    /**
     * 三路全失败：合并成一行可读错误。
     */
    @Test
    void mergeAllFailedReturnsCombinedError() {
        CreditResult merged = VolcenginePlanCreditQueryProvider.merge(
                CreditResult.fail("registry", "HTTP 401 unauthorized"),
                CreditResult.fail(VolcenginePlanCreditQueryProvider.NAME, "HTTP 401 unauthorized"),
                null);
        assertFalse(merged.isOk());
        assertTrue(merged.getError().contains("余额"), merged.getError());
        assertTrue(merged.getError().contains("套餐"), merged.getError());
    }

    /**
     * 缺 AK/SK：直接明确提示，不发请求。
     */
    @Test
    void queryWithoutAkSkFailsWithClearMessage() {
        CreditQuery query = new CreditQuery();
        query.setKind("volc-plan");
        CreditResult result = new VolcenginePlanCreditQueryProvider().query(query);
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("AK/SK"), result.getError());
    }

    /**
     * volc-ark 签名形态：POST + body 摘要参与签名，签名头域
     * content-type;host;x-content-sha256;x-date，Action/Version 保留在地址上。
     */
    @Test
    void volcArkSignProducesPostWithBodyDigest() {
        CreditQuery query = new CreditQuery();
        query.setSignType("volc-ark");
        query.setAccessKey("AKLTtest");
        query.setSecretKey("sk-test");
        query.setBody("{\"Plan\":\"CodingPlan\"}");
        CreditSigner.Signed signed = CreditSigner.sign(query,
                "https://ark.cn-beijing.volcengineapi.com/?Action=GetPersonalPlan&Version=2024-01-01");
        assertEquals("POST", signed.getMethod());
        assertTrue(signed.getUrl().startsWith("https://ark.cn-beijing.volcengineapi.com/?"), signed.getUrl());
        assertTrue(signed.getUrl().contains("Action=GetPersonalPlan"), signed.getUrl());
        assertTrue(signed.getUrl().contains("Version=2024-01-01"), signed.getUrl());
        Map<String, String> headers = signed.getHeaders();
        assertEquals("application/json", headers.get("Content-Type"));
        assertNotNull(headers.get("X-Date"));
        assertTrue(headers.get("X-Date").matches("\\d{8}T\\d{6}Z"), headers.get("X-Date"));
        assertEquals(sha256Hex("{\"Plan\":\"CodingPlan\"}"), headers.get("X-Content-Sha256"));
        String auth = headers.get("Authorization");
        assertNotNull(auth);
        assertTrue(auth.startsWith("HMAC-SHA256 Credential=AKLTtest/"), auth);
        assertTrue(auth.contains("/cn-beijing/ark/request"), auth);
        assertTrue(auth.contains("SignedHeaders=content-type;host;x-content-sha256;x-date"), auth);
        assertTrue(auth.contains("Signature="), auth);
        assertEquals("{\"Plan\":\"CodingPlan\"}", signed.getBody());
    }

    /**
     * volc-ark 空 body：按 {} 发送并签名（与实际请求字节一致）。
     */
    @Test
    void volcArkSignDefaultsEmptyBodyToEmptyJson() {
        CreditQuery query = new CreditQuery();
        query.setSignType("volc-ark");
        query.setAccessKey("AKLTtest");
        query.setSecretKey("sk-test");
        CreditSigner.Signed signed = CreditSigner.sign(query,
                "https://ark.cn-beijing.volcengineapi.com/?Action=GetCodingPlanUsage&Version=2024-01-01");
        assertEquals("{}", signed.getBody());
        assertEquals(sha256Hex("{}"), signed.getHeaders().get("X-Content-Sha256"));
    }

    /**
     * 真实链路：真点 billing 余额 + 方舟套餐 / 用量端点（不 mock）。
     *
     * <p>凭据走环境变量 / 系统属性，缺失时跳过。账号没订阅时也应成功（余额兜底），
     * 断言只锁「链路通、来源正确」，订阅状态因账号而异只打印不断言。</p>
     */
    @Test
    void realVolcEndpointsAggregateBalanceAndPlan() {
        String ak = credential("VOLC_ACCESS_KEY");
        String sk = credential("VOLC_SECRET_KEY");
        assumeTrue(ak != null && !ak.isBlank() && sk != null && !sk.isBlank(),
                "缺少 VOLC_ACCESS_KEY / VOLC_SECRET_KEY（环境变量或系统属性），跳过真实链路用例");
        CreditQuery query = new CreditQuery();
        query.setKind("volc-plan");
        query.setProviderId("doubao");
        query.setOriginUrl("https://ark.cn-beijing.volces.com/api/v3");
        query.setAccessKey(ak);
        query.setSecretKey(sk);
        CreditResult result = new VolcenginePlanCreditQueryProvider().query(query);
        assertNotNull(result);
        assertTrue(result.isOk(), "真实链路应至少查到余额或订阅之一：" + result.getError());
        assertEquals("volc-plan", result.getSource());
        System.out.println("[volc-plan] parts=" + result.getParts());
        System.out.println("[volc-plan] extra=" + result.getExtra());
    }

    /**
     * 从环境变量 / 系统属性取凭据。
     *
     * @param name 名称
     * @return 凭据；缺失为 null
     */
    private static String credential(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 取结果的 pools 数组。
     *
     * @param result 结果
     * @return pools
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> poolsOf(CreditResult result) {
        Object pools = result.getExtra().get("pools");
        assertTrue(pools instanceof List<?>, "extra.pools 应为数组");
        return (List<Map<String, Object>>) pools;
    }

    /**
     * 取池里的 windows 数组。
     *
     * @param pool 池
     * @return windows
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> windowsOf(Map<String, Object> pool) {
        Object windows = pool.get("windows");
        assertTrue(windows instanceof List<?>, "pool.windows 应为数组");
        return (List<Map<String, Object>>) windows;
    }

    /**
     * 组一段余额读数。
     *
     * @param label  段名
     * @param amount 数值
     * @return 读数
     */
    private static BalancePart part(String label, String amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(new BigDecimal(amount));
        part.setCurrency("CNY");
        return part;
    }

    /**
     * SHA-256 十六进制（与签名器同口径，校验 X-Content-Sha256 用）。
     *
     * @param data 原文
     * @return 小写十六进制
     */
    private static String sha256Hex(String data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
