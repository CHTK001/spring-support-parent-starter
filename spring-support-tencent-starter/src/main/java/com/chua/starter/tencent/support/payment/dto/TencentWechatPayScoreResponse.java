package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
* 微信支付分服务订单响应
* @author CH
* @since 4.0.0
 */
@Data
public class TencentWechatPayScoreResponse {
    /** 成功 */
    private boolean success;
    /** 出排序NO */
    private String outOrderNo;
    /** 服务排序NO */
    private String serviceOrderNo;
    /** 状态 */
    private String state;
    /** 包信息 */
    private String packageInfo;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
    private Map<String, Object> rawData; // raw数据
}

