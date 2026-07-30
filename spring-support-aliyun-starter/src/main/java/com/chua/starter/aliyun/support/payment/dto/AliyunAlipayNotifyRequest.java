package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
 * 支付宝回调请求
 */
@Data
public class AliyunAlipayNotifyRequest {
    private Map<String, String> params;
}
