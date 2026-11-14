package com.chua.starter.datasearch.credit.support;

import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 余额查询出口：按 {@link CreditQuery#getKind()} 找到实现并执行。
 *
 * <p>实现全部走 {@code META-INF/services} 登记，其它模块加实现即生效；
 * 这里只做一次装载缓存与选择，不承载任何厂商口径。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Slf4j
public final class CreditQueries {

    /**
     * 通用实现标识：表达式齐全时用它兜底
     */
    public static final String KIND_EXPRESSION = "expression";

    /**
     * 已装载的实现
     */
    private static volatile List<CreditQueryProvider> cached;

    private CreditQueries() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<CreditQueryProvider> providers() {
        List<CreditQueryProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(CreditQueryProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 全部实现的标识。
     *
     * @return 标识列表
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>();
        for (CreditQueryProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }

    /**
     * 按标识找实现。
     *
     * @param kind 标识
     * @return 实现；找不到为 null
     */
    public static CreditQueryProvider find(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        for (CreditQueryProvider provider : providers()) {
            if (kind.equalsIgnoreCase(provider.name())) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 执行一次余额查询。
     *
     * @param query 请求
     * @return 结果；未匹配到实现时 error 有值
     */
    public static CreditResult query(CreditQuery query) {
        if (query == null) {
            return CreditResult.fail(null, "余额查询请求为空");
        }
        CreditQueryProvider provider = find(query.getKind());
        if (provider == null) {
            for (CreditQueryProvider candidate : providers()) {
                if (candidate.supports(query)) {
                    provider = candidate;
                    break;
                }
            }
        }
        if (provider == null) {
            return CreditResult.fail(query.getKind(), "未匹配到余额查询实现，请配置字段表达式");
        }
        try {
            CreditResult result = provider.query(query);
            return result == null ? CreditResult.fail(provider.name(), "实现未返回结果") : result;
        } catch (RuntimeException e) {
            log.warn("[credit] 查询失败: kind={}, url={}, msg={}", provider.name(), query.getUrl(), e.getMessage());
            return CreditResult.fail(provider.name(), e.getMessage());
        }
    }
}
