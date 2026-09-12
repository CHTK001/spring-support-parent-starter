package com.chua.payment.support.dto;

import lombok.Data;

import java.util.Map;

/**
* 微信支付分取消请求
* @author CH
* @since 4.0.0
 */
@Data
public class WechatPayScoreCancelDTO {
    /** ReasonMLmlml */
    private String reason;
    private Map<String, Object> extraParams; // extra参数
    /** 评论 */
    private String remark;
}

