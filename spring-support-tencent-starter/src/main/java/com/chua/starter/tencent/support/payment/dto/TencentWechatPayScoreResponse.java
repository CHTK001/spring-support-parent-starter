package com.chua.starter.tencent.support.payment.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付分服务订单响应
 *
 * @param success 成功
 * @param outOrderNo 出排序NO
 * @param serviceOrderNo 服务排序NO
 * @param state 状态
 * @param packageInfo 包信息
 * @param message 消息
 * @param rawResponse RAW响应
 * @param rawData rawData
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatPayScoreResponse(
        boolean success,
        String outOrderNo,
        String serviceOrderNo,
        String state,
        String packageInfo,
        String message,
        String rawResponse,
        Map<String, Object> rawData
) {

    /**
     * 规范构造器：微信应答原文做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。应答来自 JSON 反序列化，
     * 保留可空语义且允许空值，故用可空安全包装而非 {@code Map.copyOf}。</p>
     *
     * @param rawData 微信应答原文
     */
    public TencentWechatPayScoreResponse {
        rawData = rawData == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(rawData));
    }
}

