package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分创建请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreCreateDTO {
    /**
     * merchantid
    */
    private Long merchantId;
    /**
     * 通道标识
    */
    private Long channelId;
    /**
     * 用户标识
    */
    private Long userId;
    /**
     * 出排序NO
    */
    private String outOrderNo;
    /**
     * 服务标识
    */
    private String serviceId;
    /**
     * 打开标识
    */
    private String openId;
    /**
     * 总数amount
    */
    private BigDecimal totalAmount;
    /**
     * 通知url
    */
    private String notifyUrl;
    /**
     * 服务introduction
    */
    private String serviceIntroduction;
    /**
     * 开始时间
    */
    private String startTime;
    /**
     * 结束时间
    */
    private String endTime;
    /**
     * ReasonMLmlml
    */
    private String reason;
    /**
     * 饰面类型
    */
    private String finishType;
    /**
     * Need用户confirm
    */
    private Boolean needUserConfirm;
    /**
     * Attach
    */
    private String attach;
    private List<Map<String, Object>> postPayments; // postpayments
    private List<Map<String, Object>> postDiscounts; // postdiscounts
    private Map<String, Object> extraParams; // extra参数
    /**
     * 评论
    */
    private String remark;
}

