package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.util.Map;

/**
* 支付宝回调请求
*
* @author CH
* @since 4.0.0.42
 */
@Data
public class AliyunAlipayNotifyRequest {

    /**
    * 回调请求参数
     */
    private Map<String, String> params;
}

