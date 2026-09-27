package com.chua.payment.support.dto;

import lombok.Data;

import java.util.Map;

/**
 * 微信支付分取消申请
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreCancelDTO {
    /**
     * 取消原因
     */
    private String reason;
    private Map<String, Object> extraParams; // extra参数
    /**
     * 备注
     */
    private String remark;
}
