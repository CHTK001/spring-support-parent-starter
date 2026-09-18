package com.chua.payment.support.spi;

/**
* SPI: 根据商户 + 用户获取第三方渠道的 打开标识。
* <p>
* 微信、支付宝等外部渠道需要平台侧将内部 用户标识 映射为渠道侧的 打开标识。
* 实现类注册为 Spring Bean 即可被自动注入使用。
* </p>
*
* @since 2026-05-05
* @author CH
 */
@FunctionalInterface
public interface UserOpenIdResolver {

    /**
    * 解析用户在指定支付渠道下的 打开标识
    *
    * @param merchantId  商户 标识
    * @param userId      系统用户 标识
    * @param channelType 渠道类型，如 WECHAT、ALIPAY
    * @return 第三方渠道的 打开标识，无可返回 空
    */
    String resolveOpenId(Long merchantId, Long userId, String channelType);
}
