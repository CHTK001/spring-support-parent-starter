package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝分账请求
 *
 * <p>对应支付宝接口 {@code alipay.trade.order.settle}。</p>
 *
 * @param outRequestNo      商户分账单号
 * @param tradeNo           支付宝交易号
 * @param royaltyParameters 分账明细列表，元素形如 {trans_in, amount, desc}
 * @param remark            备注
 * @param extraParams       透传参数
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayProfitSharingRequest(
        String outRequestNo,
        String tradeNo,
        List<Map<String, Object>> royaltyParameters,
        String remark,
        Map<String, Object> extraParams
) {

    /**
     * 规范构造器：分账明细与透传参数做防御性拷贝。
     *
     * @param royaltyParameters 分账明细列表
     * @param extraParams       透传参数
     */
    public AliyunAlipayProfitSharingRequest {
        royaltyParameters = royaltyParameters == null ? null : Collections.unmodifiableList(new ArrayList<>(royaltyParameters));
        extraParams = extraParams == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(extraParams));
    }

    /**
     * 静态金额格式化：统一两位小数字符串，避免各处重复 setScale。
     *
     * @param amount 金额
     * @return 两位小数字符串
     */
    public static String amountText(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
