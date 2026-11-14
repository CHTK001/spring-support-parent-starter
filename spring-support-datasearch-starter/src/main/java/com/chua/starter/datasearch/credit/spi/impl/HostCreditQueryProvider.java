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
 * <p>只收录「官方文档明确写了余额接口」的几家，地址与字段按官方口径填写；这些口径
 * 没有在本机逐家用真实 key 实测，页面上允许改地址 / 表达式覆盖，改完以自己的配置为准。
 * 没有余额接口的厂商不要往这里加。</p>
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
