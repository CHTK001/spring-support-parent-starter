package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 微信支付分服务订单请求
 * @author CH
 */
@Data
public class TencentWechatPayScoreRequest {
    /** APPID */
    private String appId;
    /** 服务ID */
    private String serviceId;
    /** OUT排序NO */
    private String outOrderNo;
    /** OpenID */
    private String openId;
    /** NotifyURL */
    private String notifyUrl;
    /** 服务introduction */
    private String serviceIntroduction;
    /** 开始时间 */
    private String startTime;
    /** 结束时间 */
    private String endTime;
    /** 总数amountFEN */
    private Integer totalAmountFen;
    /** Reason */
    private String reason;
    /** Finish类型 */
    private String finishType;
    /** Need用户confirm */
    private Boolean needUserConfirm;
    /** Attach */
    private String attach;
    private List<Map<String, Object>> postPayments;
    private List<Map<String, Object>> postDiscounts;
    private Map<String, Object> extraParams;
}

