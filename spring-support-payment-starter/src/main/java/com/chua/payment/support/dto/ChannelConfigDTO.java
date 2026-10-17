package com.chua.payment.support.dto;

/**
 * 渠道配置DTO
 *
 * @param merchantId     商户ID
 * @param channelType    渠道类型
 * @param channelSubType 渠道SUB类型
 * @param channelName    渠道名称
 * @param appId          APPID
 * @param merchantNo     商户号
 * @param apiKey         API密钥
 * @param privateKey     私钥字符串
 * @param publicKey      公钥字符串
 * @param certPath       证书路径
 * @param sandboxMode    Sandbox模式
 * @param notifyUrl      通知URL
 * @param returnUrl      返回值URL
 * @param status         状态
 * @param extConfig      EXT配置
 * @since 2026-03-18
 * @author CH
 */
public record ChannelConfigDTO(
        Long merchantId,
        String channelType,
        String channelSubType,
        String channelName,
        String appId,
        String merchantNo,
        String apiKey,
        String privateKey,
        String publicKey,
        String certPath,
        Integer sandboxMode,
        String notifyUrl,
        String returnUrl,
        Integer status,
        String extConfig) {
}
