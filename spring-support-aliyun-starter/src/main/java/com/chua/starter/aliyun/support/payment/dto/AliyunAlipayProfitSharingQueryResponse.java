package com.chua.starter.aliyun.support.payment.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 支付宝分账查询响应
 *
 * <p>对应支付宝接口 {@code alipay.trade.order.settle.query} 的 {@code royalty_detail_list}。</p>
 *
 * @param success        是否成功
 * @param outRequestNo   商户分账单号
 * @param tradeNo        支付宝交易号
 * @param royaltyDetails 分账明细列表(原始)
 * @param message        消息
 * @param rawBody        原始响应体
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayProfitSharingQueryResponse(
        boolean success,
        String outRequestNo,
        String tradeNo,
        List<Map<String, Object>> royaltyDetails,
        String message,
        String rawBody
) {

    /**
     * 规范构造器：分账明细做防御性拷贝。
     *
     * @param royaltyDetails 分账明细列表
     */
    public AliyunAlipayProfitSharingQueryResponse {
        royaltyDetails = royaltyDetails == null ? null : Collections.unmodifiableList(new ArrayList<>(royaltyDetails));
    }
}
