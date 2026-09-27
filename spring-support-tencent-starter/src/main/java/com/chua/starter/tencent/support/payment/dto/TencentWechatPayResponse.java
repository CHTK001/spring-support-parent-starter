package com.chua.starter.tencent.support.payment.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信下单响应
 *
 * @param success 成功
 * @param payUrl PAYURL
 * @param sdkParams sdkParams
 * @param message 消息
 * @param rawResponse RAW响应
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatPayResponse(
        boolean success,
        String payUrl,
        Map<String, Object> sdkParams,
        String message,
        String rawResponse
) {

    /**
     * 规范构造器：调起支付 SDK 参数做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。H5 与 Native 下单不产出该参数，
     * 保留可空语义；取值来自微信应答，允许存在空值，故用可空安全包装而非
     * {@code Map.copyOf}。</p>
     *
     * @param sdkParams 调起支付 SDK 参数
     */
    public TencentWechatPayResponse {
        sdkParams = sdkParams == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(sdkParams));
    }
}

