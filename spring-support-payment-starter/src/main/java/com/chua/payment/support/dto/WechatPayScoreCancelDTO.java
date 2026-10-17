package com.chua.payment.support.dto;

import java.util.Map;

/**
 * 微信支付分取消申请
 *
 * @param reason      取消原因
 * @param extraParams extra参数
 * @param remark      备注
 * @author CH
 * @since 4.0.0
 */
public record WechatPayScoreCancelDTO(
        String reason,
        Map<String, Object> extraParams,
        String remark) {
}
