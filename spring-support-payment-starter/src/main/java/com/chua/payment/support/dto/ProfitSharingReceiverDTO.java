package com.chua.payment.support.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分账接收方创建/更新参数
 *
 * @param merchantId   商户ID
 * @param channelType  渠道类型: WECHAT/ALIPAY
 * @param name         接收方名称
 * @param account      接收方账号(商户号/支付宝用户号/登录账号)
 * @param type         账号类型: MERCHANT_ID/PERSONAL_OPENID/LOGIN_NAME
 * @param relationType 与商户关系类型: PARTNER/SERVICE_PROVIDER/STORE/STAFF
 * @param ratio        分账比例(%)
 * @param remark       备注
 * @author CH
 * @since 4.0.0
 */
public record ProfitSharingReceiverDTO(
        Long merchantId,
        String channelType,
        String name,
        String account,
        String type,
        String relationType,
        BigDecimal ratio,
        String remark
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
