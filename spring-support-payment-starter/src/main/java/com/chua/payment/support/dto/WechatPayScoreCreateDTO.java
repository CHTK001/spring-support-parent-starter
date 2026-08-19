package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分创建请求
 * @author CH
 */
@Data
public class WechatPayScoreCreateDTO {
    /** MerchantID */
    private Long merchantId;
    /** 通道ID */
    private Long channelId;
    /** 用户ID */
    private Long userId;
    /** OUT排序NO */
    private String outOrderNo;
    /** 服务ID */
    private String serviceId;
    /** OpenID */
    private String openId;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** NotifyURL */
    private String notifyUrl;
    /** 服务introduction */
    private String serviceIntroduction;
    /** 开始时间 */
    private String startTime;
    /** 结束时间 */
    private String endTime;
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
    /** Remark */
    private String remark;
}

