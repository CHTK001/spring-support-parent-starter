package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分账接收方视图对象
 *
 * @param id           主键ID
 * @param merchantId   商户ID
 * @param channelType  渠道类型: WECHAT/ALIPAY
 * @param name         接收方名称
 * @param account      接收方账号
 * @param type         账号类型: MERCHANT_ID/PERSONAL_OPENID/LOGIN_NAME
 * @param relationType 与商户关系类型: PARTNER/SERVICE_PROVIDER/STORE/STAFF
 * @param ratio        分账比例(%)
 * @param status       状态: ENABLED/DISABLED
 * @param statusDesc   状态描述
 * @param remark       备注
 * @param createdAt    创建时间
 * @since 4.0.0
 * @author CH
 */
public record ProfitSharingReceiverVO(
        Long id,
        Long merchantId,
        String channelType,
        String name,
        String account,
        String type,
        String relationType,
        BigDecimal ratio,
        String status,
        String statusDesc,
        String remark,
        LocalDateTime createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
