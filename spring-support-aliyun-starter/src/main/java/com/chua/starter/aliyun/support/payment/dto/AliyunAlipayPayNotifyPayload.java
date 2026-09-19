package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付通知解析结果
 * @author CH
 * @since 4.0.0
 */
@Data
public class AliyunAlipayPayNotifyPayload {
    /** 排序NO */
    private String orderNo;
    /** 贸易no */
    private String tradeNo;
    /** APPID */
    private String appId;
    /** sellerid */
    private String sellerId;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** 贸易状态 */
    private String tradeStatus;
    private Map<String, String> rawParams; // raw参数
}

