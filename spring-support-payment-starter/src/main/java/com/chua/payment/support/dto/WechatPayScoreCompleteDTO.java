package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分完结请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreCompleteDTO {
    /** 总数amount */
    private BigDecimal totalAmount;
    /** 饰面类型 */
    private String finishType;
    /** ReasonMLmlml */
    private String reason;
    /** 结束时间 */
    private String endTime;
    private List<Map<String, Object>> postPayments; // postpayments
    private List<Map<String, Object>> postDiscounts; // postdiscounts
    private Map<String, Object> extraParams; // extra参数
    /** 评论 */
    private String remark;
}

