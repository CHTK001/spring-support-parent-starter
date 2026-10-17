package com.chua.payment.support.dto;

import java.io.Serializable;

/**
 * 客户数据传输对象
 *
 * @param merchantName                    商户名称
 * @param contactName                     联系人名称
 * @param contactPhone                    联系电话
 * @param contactEmail                    联系邮箱
 * @param businessLicense                 营业执照
 * @param legalPerson                     法人
 * @param defaultNotifyUrl                默认通知URL
 * @param defaultReturnUrl                默认返回值URL
 * @param paymentWalletEnabled            支付钱包是否启用
 * @param paymentAutoCloseEnabled         支付自动关闭是否启用
 * @param paymentAutoCloseMinutes         支付自动关闭分钟数
 * @param paymentSplitTableEnabled        支付分表是否启用
 * @param paymentProfitSharingEnabled     支付分账是否启用
 * @param paymentCouponEnabled            支付优惠券是否启用
 * @param remark                          备注
 * @since 2026-03-18
 * @author CH
 */
public record MerchantDTO(
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
        String remark) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
