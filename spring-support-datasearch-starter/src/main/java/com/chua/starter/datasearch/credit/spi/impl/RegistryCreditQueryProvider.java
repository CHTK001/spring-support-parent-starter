package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.model.ProviderCreditEndpoint;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.chua.starter.datasearch.credit.support.ProviderCreditEndpoints;

/**
 * 官方余额实现：口径来自数据登记表 {@code provider-credit-endpoints.json}。
 *
 * <p>与 {@link HostCreditQueryProvider} 的区别只在标识：{@code registry} 是新口径，
 * {@code host} 保留给历史配置；两者读同一份登记表，不会漂移。新增一家官方余额接口
 * 只需加一条 JSON，不用改代码。</p>
 *
 * <p>查询前先做同源判断：拿别家的 Key 去问这家官方接口只会得到 401，
 * 已跳过并给出明确原因，不把「Key 无效」这种假结论写进页面。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Spi("registry")
public class RegistryCreditQueryProvider implements CreditQueryProvider {

    @Override
    public String name() {
        return "registry";
    }

    /**
     * 按登记表口径查询余额。
     *
     * @param query 请求
     * @return 结果；厂商未收录、不同源或查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        String providerId = query.getProviderId() == null
                ? null : query.getProviderId().trim().toLowerCase(java.util.Locale.ROOT);
        ProviderCreditEndpoint endpoint = ProviderCreditEndpoints.find(providerId);
        if (endpoint == null) {
            return CreditResult.fail(name(), "未收录该厂商的余额接口: " + query.getProviderId());
        }
        // 兜底：即使调用方没先筛候选，也不拿别家的 Key 去问这家的官方接口。
        // 账户级 AK/SK 签名类（腾讯 / 华为等）例外：凭据是云账号级的，不属于某个 API 域名，
        // 厂商身份由登记表品牌判定；计费域名与模型域名不同根属正常，不适用同源兜底
        if (endpoint.getSignType() == null
                && !ProviderCreditEndpoints.sameOrigin(providerId, query.getOriginUrl())) {
            return CreditResult.fail(name(),
                    "Key 属于 " + ProviderCreditEndpoints.hostOf(query.getOriginUrl()) + "，不是 "
                            + providerId + " 的站点，已跳过官方余额接口");
        }
        try {
            String url = query.getUrl() == null || query.getUrl().isBlank()
                    ? endpoint.getUrl() : query.getUrl();
            // AK/SK 签名类由登记表注入签名方案，交给 CreditSigner / CreditHttp 独立出口处理；
            // 用户若已显式指定签名方案则尊重用户配置，不覆盖。
            if (endpoint.getSignType() != null
                    && (query.getSignType() == null || query.getSignType().isBlank())) {
                query.setSignType(endpoint.getSignType());
            }
            // 登记表补充请求方法与自定义鉴权头（如 NanoGPT 的 POST + x-api-key）；页面显式配置优先
            if (endpoint.getMethod() != null
                    && (query.getMethod() == null || query.getMethod().isBlank())) {
                query.setMethod(endpoint.getMethod());
            }
            if (endpoint.getAuthHeader() != null
                    && (query.getTokenHeader() == null || query.getTokenHeader().isBlank())) {
                query.setTokenHeader(endpoint.getAuthHeader());
            }
            String raw = CreditHttp.fetch(query, url);
            String expression = query.getExpression() == null || query.getExpression().isBlank()
                    ? endpoint.getExpression() : query.getExpression();
            String billing = query.getBilling() == null || query.getBilling().isBlank()
                    ? endpoint.getCurrency() : query.getBilling();
            return CreditExpressions.parse(expression, raw, name(), billing);
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }
}
