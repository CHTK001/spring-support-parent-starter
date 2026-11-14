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
 * <p>只收录「官方文档明确写了余额 / 额度接口」的厂商，地址与字段按官方文档原文填写。
 * 分两类：</p>
 * <ul>
 *   <li><b>Bearer 类</b>（11 家）：地址与字段逐条用假令牌探活过路由（真路径不是 404、
 *       同前缀捏造的路径 404，才是真的存在）；这些口径没有在本机用真实 key 逐家实测，
 *       页面上允许改地址 / 表达式覆盖，改完以自己的配置为准。</li>
 *   <li><b>AK/SK 签名类</b>（4 家：阿里云 BSS / 火山 billing / 百度财务 / 七牛）：
 *       端点与返回字段同样来自官方文档原文，但要按各家规范对请求做 HMAC 签名，
 *       由 {@code CreditSigner} 完成，缺 AK/SK 时返回明确提示而不是发一个必然 401 的请求。
 *       这四家没做过真链路探活（不允许向厂商端点发请求），字段口径若与上游不一致，
 *       页面用 {@code credit_expression} 覆盖即可。</li>
 * </ul>
 *
 * <p>凡是官方文档没写余额 / 额度端点的厂商一律不收录、不猜地址；
 * 需要支持的，走通用「地址 + 表达式」配置，不在这里加。</p>
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
                "!usage.rolling.percent; !usage.weekly.percent; !usage.monthly.percent", null));
        // ===== 以下 4 家为 AK/SK 签名类：端点与字段均来自官方文档原文，签名由 CreditSigner 完成。
        // 未做真链路探活（本阶段不向厂商端点发请求），字段口径若与上游不一致，页面用 credit_expression 覆盖。
        // 阿里云 BSS QueryAccountBalance（BssOpenApi 2017-12-14，business.aliyuncs.com，RPC 老签名 HMAC-SHA1）：
        // 响应 Data.AvailableAmount / AvailableCashAmount / Currency，金额单位与币种原文一致。
        HOSTS.put("aliyun-bss", new HostSpec("https://business.aliyuncs.com/",
                "¥Data.AvailableAmount; ¥Data.AvailableCashAmount", "aliyun-bss"));
        // 火山引擎 QueryBalanceAcct（billing.volcengineapi.com，2022-01-01，HMAC-SHA256）：
        // 响应 Result.AvailableBalance / CashBalance / ArrearsBalance / CreditLimit / FreezeAmount。
        HOSTS.put("volc-billing", new HostSpec("https://billing.volcengineapi.com/",
                "¥Result.AvailableBalance; ¥Result.CashBalance; ¥Result.ArrearsBalance", "volc"));
        // 百度智能云财务现金余额（POST /v1/finance/cash/balance，billing.baidubce.com，bce-auth-v1）：
        // 响应 cashBalance（字符串金额，如 "850.0000"）。
        HOSTS.put("baidu-bce", new HostSpec("https://billing.baidubce.com/v1/finance/cash/balance",
                "¥cashBalance", "bce"));
        // 七牛财务账户余额概览（GET /billing-api/v1/account/balance-overview，api.qiniu.com，Qiniu 管理凭证）：
        // 响应 data.available_balance / cash_balance 等为 8 位小数整数，需 / 100000000 换算成元。
        HOSTS.put("qiniu", new HostSpec("https://api.qiniu.com/billing-api/v1/account/balance-overview",
                "¥data.available_balance / 100000000; ¥data.cash_balance / 100000000", "qiniu"));
    }

    @Override
    public String name() {
        return "host";
    }

    /**
     * 是否收录了「Bearer 类」厂商的余额端点。
     *
     * <p>供 AI 供应商余额面板判断「要不要自动加一条 host 候选」：那侧按 API Key 逐个查，
     * 拿不到 AK/SK，所以 4 家签名类预设不参与自动候选（命中只会多一条必然 401 的失败行）。
     * 账户侧走显式 {@code kind=host} + providerId，仍可命中全部 15 个预设，不受此判断影响。</p>
     *
     * <p>本集合与 {@code ApiProviderController.CREDIT_HOST_BRANDS}（11 家）保持一致。</p>
     *
     * @param providerId 厂商标识
     * @return 是 Bearer 类预设为 true
     */
    public static boolean supportsHost(String providerId) {
        if (providerId == null) {
            return false;
        }
        HostSpec spec = HOSTS.get(providerId.trim().toLowerCase());
        return spec != null && spec.signType == null;
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
            // AK/SK 签名类由预设注入签名方案，交给 CreditSigner / CreditHttp 独立出口处理；
            // 用户若已显式指定签名方案则尊重用户配置，不覆盖。
            if (spec.signType != null && (query.getSignType() == null || query.getSignType().isBlank())) {
                query.setSignType(spec.signType);
            }
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

        /**
         * 签名方案（qiniu / volc / bce / aliyun-bss），为空表示走 Bearer
         */
        private final String signType;

        private HostSpec(String url, String expression) {
            this(url, expression, null);
        }

        private HostSpec(String url, String expression, String signType) {
            this.url = url;
            this.expression = expression;
            this.signType = signType;
        }
    }
}
