package com.chua.payment.support.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分取消请求
 * @author CH
 */
@Data
public class WechatPayScoreCancelDTO {
    /** Reason */
    private String reason;
    private Map<String, Object> extraParams;
    /** Remark */
    private String remark;
}

