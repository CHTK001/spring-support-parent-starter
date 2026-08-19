package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分服务订单响应
 * @author CH
 */
@Data
public class TencentWechatPayScoreResponse {
    /** Success */
    private boolean success;
    /** OUT排序NO */
    private String outOrderNo;
    /** 服务排序NO */
    private String serviceOrderNo;
    /** 状态 */
    private String state;
    /** Package信息 */
    private String packageInfo;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
    private Map<String, Object> rawData;
}

