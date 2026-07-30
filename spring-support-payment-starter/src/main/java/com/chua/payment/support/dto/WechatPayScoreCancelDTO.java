package com.chua.payment.support.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分取消请求
 */
@Data
public class WechatPayScoreCancelDTO {
    private String reason;
    private Map<String, Object> extraParams;
    private String remark;
}
