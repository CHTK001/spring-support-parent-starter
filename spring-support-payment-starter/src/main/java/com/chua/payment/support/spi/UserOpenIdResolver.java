package com.chua.payment.support.spi;

/**
 * SPI: 根据商户 + 用户获取第三方通道的开放标识
 * <p>
 * 微信、支付宝等外部通道需要平台侧将内部用户标识 映射为通道侧的开放标识，
 * 实现类注册为 Spring Bean 即可被自动注入使用。
 * </p>
 *
 * @since 2026-05-05
 * @author CH
 */
@FunctionalInterface
public interface UserOpenIdResolver {

    /**
     * 解析用户在指定支付通道下的开放标识
     *
     * @param merchantId  商户标识
     * @param userId      系统用户标识
     * @param channelType 通道类型，如 WECHAT、ALIPAY
     * @return 第三方通道的开放标识，无可返回值
     */
    String resolveOpenId(Long merchantId, Long userId, String channelType);
}
