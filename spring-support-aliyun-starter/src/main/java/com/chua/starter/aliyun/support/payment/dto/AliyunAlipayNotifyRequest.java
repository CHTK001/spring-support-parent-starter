package com.chua.starter.aliyun.support.payment.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 支付宝回调请求
 *
 * @param params 回调请求参数
 *
 * @author CH
 * @since 4.0.0.42
 */
public record AliyunAlipayNotifyRequest(
        Map<String, String> params
) {

    /**
     * 规范构造器：对回调参数做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变；回调参数值允许为空，
     * 故采用允许空值的不可变包装而非 {@code Map.copyOf}。</p>
     *
     * @param params 回调请求参数
     */
    public AliyunAlipayNotifyRequest {
        params = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(params, "params 不能为 null")));
    }
}

