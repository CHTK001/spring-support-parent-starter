package com.chua.payment.support.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户支付渠道视图对象
 *
 * @param id                   标识
 * @param merchantId           商户ID
 * @param merchantName         商户名称
 * @param channelType          渠道类型
 * @param channelSubType       渠道SUB类型
 * @param channelName          渠道名称
 * @param appId                APPID
 * @param merchantNo           商户号
 * @param apiKeyConfigured     API密钥是否已配置
 * @param privateKeyConfigured 私钥是否已配置
 * @param publicKeyConfigured  公钥是否已配置
 * @param certConfigured       证书是否已配置
 * @param sandboxMode          Sandbox模式
 * @param notifyUrl            通知URL
 * @param returnUrl            返回值URL
 * @param status               状态
 * @param statusDesc           状态描述
 * @param extConfig            EXT配置
 * @param guideTitle           引导标题
 * @param guideUrl             引导链接
 * @param channelTypeDesc      渠道类型描述
 * @param effectiveNotifyUrl   生效通知URL
 * @param effectiveReturnUrl   生效返回值URL
 * @param createdAt            创建时间
 * @param updatedAt            更新时间
 * @author CH
 * @since 4.0.0
 */
public record MerchantChannelVO(
        Long id,
        Long merchantId,
        String merchantName,
        String channelType,
        String channelSubType,
        String channelName,
        String appId,
        String merchantNo,
        Boolean apiKeyConfigured,
        Boolean privateKeyConfigured,
        Boolean publicKeyConfigured,
        Boolean certConfigured,
        Integer sandboxMode,
        String notifyUrl,
        String returnUrl,
        Integer status,
        String statusDesc,
        String extConfig,
        String guideTitle,
        String guideUrl,
        String channelTypeDesc,
        String effectiveNotifyUrl,
        String effectiveReturnUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
