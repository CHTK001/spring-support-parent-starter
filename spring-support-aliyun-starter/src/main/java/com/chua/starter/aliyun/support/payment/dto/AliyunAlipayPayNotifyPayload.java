package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付通知解析结果
 * @author CH
 */
@Data
public class AliyunAlipayPayNotifyPayload {
    /** 排序NO */
    private String orderNo;
    /** TradeNO */
    private String tradeNo;
    /** APPID */
    private String appId;
    /** SellerID */
    private String sellerId;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** Trade状态 */
    private String tradeStatus;
    private Map<String, String> rawParams;
}

