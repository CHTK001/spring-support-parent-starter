package com.chua.payment.support.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分完结请求
 *
 * @param totalAmount   总金额
 * @param finishType    完成类型
 * @param reason        完结原因
 * @param endTime       结束时间
 * @param postPayments  后付商品列表
 * @param postDiscounts 后付优惠列表
 * @param extraParams   extra参数
 * @param remark        备注
 * @author CH
 * @since 4.0.0
 */
public record WechatPayScoreCompleteDTO(
        BigDecimal totalAmount,
        String finishType,
        String reason,
        String endTime,
        List<Map<String, Object>> postPayments,
        List<Map<String, Object>> postDiscounts,
        Map<String, Object> extraParams,
        String remark) {
}
