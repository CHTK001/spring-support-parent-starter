package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付请求
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class PaymentRequest {
    /**
     * 排序NO
    */
    private String orderNo;
    /**
     * 用户标识
    */
    private Long userId;
    /**
     * Amount
    */
    private BigDecimal amount;
    /**
     * 主题
    */
    private String subject;
    /**
     * 请求体
    */
    private String body;
    /**
     * 通知url
    */
    private String notifyUrl;
    /**
     * 返回值URL
    */
    private String returnUrl;
    /**
     * 货币
    */
    private String currency;
    /**
     * Expire时间
    */
    private LocalDateTime expireTime;
    /**
     * payeropenid
    */
    private String payerOpenId;
    /**
     * 客户端IP
    */
    private String clientIp;
    /**
     * deviceid
    */
    private String deviceId;
    /**
     * 用户Agent
    */
    private String userAgent;
    /**
     * Attach
    */
    private String attach;
}
