package com.chua.payment.support.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户视图对象
 *
 * @param id                          标识
 * @param merchantNo                  商户号
 * @param merchantName                商户名称
 * @param contactName                 联系人名称
 * @param contactPhone                联系电话
 * @param contactEmail                联系邮箱
 * @param businessLicense             营业执照
 * @param legalPerson                 法人
 * @param defaultNotifyUrl            默认通知URL
 * @param defaultReturnUrl            默认返回值URL
 * @param paymentWalletEnabled        支付钱包是否启用
 * @param paymentAutoCloseEnabled     支付自动关闭是否启用
 * @param paymentAutoCloseMinutes     支付自动关闭分钟数
 * @param paymentSplitTableEnabled    支付分表是否启用
 * @param paymentProfitSharingEnabled 支付分账是否启用
 * @param paymentCouponEnabled        支付优惠券是否启用
 * @param remark                      备注
 * @param status                      状态
 * @param statusDesc                  状态描述
 * @param channelCount                渠道数量
 * @param createdAt                   创建时间
 * @param updatedAt                   更新时间
 * @since 2026-03-18
 * @author CH
 */
public record MerchantVO(
        Long id,
        String merchantNo,
        String merchantName,
        String contactName,
        String contactPhone,
        String contactEmail,
        String businessLicense,
        String legalPerson,
        String defaultNotifyUrl,
        String defaultReturnUrl,
        Boolean paymentWalletEnabled,
        Boolean paymentAutoCloseEnabled,
        Integer paymentAutoCloseMinutes,
        Boolean paymentSplitTableEnabled,
        Boolean paymentProfitSharingEnabled,
        Boolean paymentCouponEnabled,
        String remark,
        Integer status,
        String statusDesc,
        Integer channelCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
