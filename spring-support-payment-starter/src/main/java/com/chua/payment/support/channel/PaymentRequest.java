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
     * 订单号
     */
    private String orderNo;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 支付金额
     */
    private BigDecimal amount;
    /**
     * 支付主题
     */
    private String subject;
    /**
     * 请求体
     */
    private String body;
    /**
     * 通知回调 URL
     */
    private String notifyUrl;
    /**
     * 支付完成后返回 URL
     */
    private String returnUrl;
    /**
     * 币种
     */
    private String currency;
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    /**
     * 付款人 openId
     */
    private String payerOpenId;
    /**
     * 客户端 IP
     */
    private String clientIp;
    /**
     * 设备标识
     */
    private String deviceId;
    /**
     * 用户 Agent
     */
    private String userAgent;
    /**
     * 附加数据
     */
    private String attach;
}
