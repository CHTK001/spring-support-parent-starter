package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分完结请求
 */
@Data
public class WechatPayScoreCompleteDTO {
    private BigDecimal totalAmount;
    private String finishType;
    private String reason;
    private String endTime;
    private List<Map<String, Object>> postPayments;
    private List<Map<String, Object>> postDiscounts;
    private Map<String, Object> extraParams;
    private String remark;
}
