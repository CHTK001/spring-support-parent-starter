package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝下单请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class AliyunAlipayPayRequest {
    /**
     * 排序NO
    */
    private String orderNo;
    /**
     * 总数amount
    */
    private BigDecimal totalAmount;
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
     * Product代码
    */
    private String productCode;
    /**
     * payeropenid
    */
    private String payerOpenId;
}

