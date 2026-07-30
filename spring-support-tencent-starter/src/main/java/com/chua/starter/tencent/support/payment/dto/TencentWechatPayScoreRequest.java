package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 微信支付分服务订单请求
 */
@Data
public class TencentWechatPayScoreRequest {
    private String appId;
    private String serviceId;
    private String outOrderNo;
    private String openId;
    private String notifyUrl;
    private String serviceIntroduction;
    private String startTime;
    private String endTime;
    private Integer totalAmountFen;
    private String reason;
    private String finishType;
    private Boolean needUserConfirm;
    private String attach;
    private List<Map<String, Object>> postPayments;
    private List<Map<String, Object>> postDiscounts;
    private Map<String, Object> extraParams;
}
