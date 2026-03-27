package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分创建请求
 */
@Data
public class WechatPayScoreCreateDTO {
    private Long merchantId;
    private Long channelId;
    private Long userId;
    private String outOrderNo;
    private String serviceId;
    private String openId;
    private BigDecimal totalAmount;
    private String notifyUrl;
    private String serviceIntroduction;
    private String startTime;
    private String endTime;
    private String reason;
    private String finishType;
    private Boolean needUserConfirm;
    private String attach;
    private List<Map<String, Object>> postPayments;
    private List<Map<String, Object>> postDiscounts;
    private Map<String, Object> extraParams;
    private String remark;
}
