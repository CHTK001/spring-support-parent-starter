package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝分账回退请求
 *
 * <p>SDK 未提供独立的分账回退接口模型，按支付宝官方口径走
 * {@code alipay.trade.refund} 携带 {@code refund_royalty_parameters} 实现退分账。</p>
 *
 * @param outBizNo     商户回退单号
 * @param tradeNo      支付宝交易号
 * @param transIn      原分账接收方账号
 * @param returnAmount 回退金额
 * @param reason       回退原因
 * @param extraParams  透传参数
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayProfitSharingReturnRequest(
        String outBizNo,
        String tradeNo,
        String transIn,
        BigDecimal returnAmount,
        String reason,
        Map<String, Object> extraParams
) {

    /**
     * 规范构造器：透传参数做防御性拷贝。
     *
     * @param extraParams 透传参数
     */
    public AliyunAlipayProfitSharingReturnRequest {
        extraParams = extraParams == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(extraParams));
    }
}
