package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.support.ProviderCreditEndpoints;

import java.util.Set;

/**
 * 厂商官方余额接口（历史标识 {@code host}）。
 *
 * <p>口径已迁到数据登记表 {@code provider-catalog/provider-credit-endpoints.json}，
 * 与 {@link RegistryCreditQueryProvider} 读同一份数据：这里保留 {@code host} 标识与
 * 静态判定方法，历史配置（{@code extra.credit_kind=host}）与 AI 供应商余额面板
 * 无需改动即可继续工作。</p>
 *
 * <p>登记表只收录「官方文档明确写了余额 / 额度接口」的厂商：11 家 Bearer 口径
 * （地址与字段逐条用假令牌探活过路由）与 4 家账户级 AK/SK 签名类（阿里云 BSS /
 * 火山 billing / 百度财务 / 七牛）。官方文档没写余额端点的厂商一律不收录、不猜地址，
 * 走通用「地址 + 表达式」配置。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("host")
public class HostCreditQueryProvider extends RegistryCreditQueryProvider {

    @Override
    public String name() {
        return "host";
    }

    /**
     * 是否收录了「Bearer 类」厂商的余额端点。
     *
     * <p>供 AI 供应商余额面板判断「要不要自动加一条 host 候选」：那侧按 API Key 逐个查，
     * 拿不到 AK/SK，所以 4 家签名类预设不参与自动候选（命中只会多一条必然 401 的失败行）。</p>
     *
     * @param providerId 厂商标识
     * @return 是 Bearer 类预设为 true
     */
    public static boolean supportsHost(String providerId) {
        return ProviderCreditEndpoints.supportsHost(providerId);
    }

    /**
     * 全部已收录的厂商标识（含 AK/SK 签名类）。
     *
     * @return 厂商标识集合（只读）
     */
    public static Set<String> brands() {
        return ProviderCreditEndpoints.brands();
    }

    /**
     * Bearer 类厂商标识（不含 AK/SK 签名类）。
     *
     * @return 厂商标识集合（只读）
     */
    public static Set<String> bearerBrands() {
        return ProviderCreditEndpoints.bearerBrands();
    }

    /**
     * 预设域名是否与 Key 所属站点一致。
     *
     * @param providerId 厂商标识
     * @param baseUrl    供应商配置里的上游地址
     * @return 一致或无法判断时 {@code true}
     */
    public static boolean sameOrigin(String providerId, String baseUrl) {
        return ProviderCreditEndpoints.sameOrigin(providerId, baseUrl);
    }
}
