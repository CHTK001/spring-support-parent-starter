package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分完结请求
 * @author CH
 */
@Data
public class WechatPayScoreCompleteDTO {
    /** 总数amount */
    private BigDecimal totalAmount;
    /** Finish类型 */
    private String finishType;
    /** Reason */
    private String reason;
    /** 结束时间 */
    private String endTime;
    private List<Map<String, Object>> postPayments;
    private List<Map<String, Object>> postDiscounts;
    private Map<String, Object> extraParams;
    /** Remark */
    private String remark;
}

