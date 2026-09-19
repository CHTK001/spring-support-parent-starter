package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 微信支付分服务订单请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatPayScoreRequest {
    /**
     * APPID
    */
    private String appId;
    /**
     * 服务标识
    */
    private String serviceId;
    /**
     * 出排序NO
    */
    private String outOrderNo;
    /**
     * 打开标识
    */
    private String openId;
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
     * 总数amountfen
    */
    private Integer totalAmountFen;
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
}

