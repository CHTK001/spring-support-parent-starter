package com.chua.starter.datasearch.credit.spi;

import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;

/**
 * 余额查询扩展接口。
 *
 * <p>各家「能查到余额」的地址、鉴权方式、返回结构都不一样，这里把差异收进实现：
 * 调用方只给 {@link CreditQuery}，实现负责拼地址、带令牌、取字段。新增一家只要加一个
 * 实现并在 {@code META-INF/services} 里登记，其它模块无需改动。</p>
 *
 * <p>实现约定：查不到不许抛异常，返回 {@link CreditResult#fail(String, String)}。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public interface CreditQueryProvider {

    /**
     * 实现标识，与 {@link CreditQuery#getKind()} 对应。
     *
     * @return 实现标识
     */
    String name();

    /**
     * 判断本次请求是否归本实现处理。
     *
     * @param query 请求
     * @return 能处理时为 true
     */
    default boolean supports(CreditQuery query) {
        return query != null && name().equalsIgnoreCase(query.getKind());
    }

    /**
     * 执行余额查询。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    CreditResult query(CreditQuery query);
}
