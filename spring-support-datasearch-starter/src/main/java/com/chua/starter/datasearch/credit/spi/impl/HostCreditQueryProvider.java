package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 厂商官方余额接口（按厂商标识取默认地址与表达式）。
 *
 * <p>只收录「官方文档明确写了余额 / 额度接口」的几家，地址与字段按官方文档原文填写，
 * 并逐条用假令牌探活过路由（真路径不是 404、同前缀捏造的路径 404，才是真的存在）。
 * 这些口径没有在本机用真实 key 逐家实测，页面上允许改地址 / 表达式覆盖，改完以自己的配置为准。
 * 没有余额接口、或要 AK/SK 签名（我们只发 Bearer）的厂商不要往这里加。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("host")
public class HostCreditQueryProvider implements CreditQueryProvider {

    /**
     * 厂商标识 → 默认口径
     */
    private static final Map<String, HostSpec> HOSTS = new LinkedHashMap<>();

    static {
        HOSTS.put("deepseek", new HostSpec("https://api.deepseek.com/user/balance",
                "¥balance_infos.0.total_balance; ¥balance_infos.0.granted_balance"));
        HOSTS.put("moonshot", new HostSpec("https://api.moonshot.cn/v1/users/me/balance",
                "¥data.available_balance; ¥data.voucher_balance"));
        HOSTS.put("siliconflow", new HostSpec("https://api.siliconflow.cn/v1/user/info",
                "¥data.totalBalance; ¥data.balance"));
        HOSTS.put("openrouter", new HostSpec("https://openrouter.ai/api/v1/credits",
                "$data.total_credits; $data.total_usage"));
        // 以下六家的地址/字段都是从官方文档页原文里逐字抠出来的，并用假令牌探活过
        //（真路径不是 404、同前缀捏造的路径 404）；但都还没用真实 key 跑过一次，
        // 首次绑真 key 时页面会显示上游原文，地址与表达式都可在页面上覆盖。
        HOSTS.put("novita-ai", new HostSpec("https://api.novita.ai/openapi/v1/billing/balance/detail",
                "$availableBalance / 10000; $cashBalance / 10000"));
        HOSTS.put("requesty", new HostSpec("https://api-v2.requesty.ai/v1/manage/org",
                "$balance"));
        HOSTS.put("llmgateway", new HostSpec("https://api.llmgateway.io/v1/key",
                "$data.devPlanCreditsRemaining"));
        HOSTS.put("deepinfra", new HostSpec("https://api.deepinfra.com/payment/usage?from=current",
                "$months.0.total_cost"));
        HOSTS.put("together", new HostSpec("https://api.together.ai/v1/billing/usage",
                "$data.0.line_items.0.cost"));
        HOSTS.put("stepfun", new HostSpec("https://api.stepfun.com/v1/accounts",
                "¥balance; ¥total_cash_balance; ¥total_voucher_balance"));
        // OpenCode Go 订阅没有绝对额度，只回三个窗口的「已用百分比」（! 前缀 = 100 - 已用），
        // 三条一起读，页面按第一条（滚动 5 小时）当主读数。
        // 注意：这条路径不在官方文档里（文档只说「用量去 Console 看」，唯一的公开读数是
        // 每次响应的 usage.opencode.allowance），是按 magpie 社区插件口径查的，
        // 并用假令牌探活确认存在（真路径 401，同前缀捏造路径 404）。
        HOSTS.put("opencode-go", new HostSpec("https://opencode.ai/zen/go/v1/usage",
                "!usage.rolling.percent; !usage.weekly.percent; !usage.monthly.percent"));
    }

    @Override
    public String name() {
        return "host";
    }

    /**
     * 是否收录了该厂商。
     *
     * @param providerId 厂商标识
     * @return 收录为 true
     */
    public static boolean supportsHost(String providerId) {
        return providerId != null && HOSTS.containsKey(providerId.trim().toLowerCase());
    }

    /**
     * 按厂商口径查询余额。
     *
     * @param query 请求
     * @return 结果；厂商未收录或查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        String providerId = query.getProviderId() == null ? null : query.getProviderId().trim().toLowerCase();
        HostSpec spec = providerId == null ? null : HOSTS.get(providerId);
        if (spec == null) {
            return CreditResult.fail(name(), "未收录该厂商的余额接口: " + query.getProviderId());
        }
        try {
            String url = query.getUrl() == null || query.getUrl().isBlank() ? spec.url : query.getUrl();
            String raw = CreditHttp.fetch(query, url);
            String expression = query.getExpression() == null || query.getExpression().isBlank()
                    ? spec.expression : query.getExpression();
            return CreditExpressions.parse(expression, raw, name(), query.getBilling());
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 厂商余额口径。
     */
    private static final class HostSpec {

        /**
         * 默认地址
         */
        private final String url;

        /**
         * 默认表达式
         */
        private final String expression;

        private HostSpec(String url, String expression) {
            this.url = url;
            this.expression = expression;
        }
    }
}
