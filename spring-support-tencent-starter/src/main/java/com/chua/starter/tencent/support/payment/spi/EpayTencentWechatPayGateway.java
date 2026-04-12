package com.chua.starter.tencent.support.payment.spi;

import com.chua.common.support.core.annotation.Spi;

/**
 * 易支付微信网关 SPI 别名实现，当前复用默认微信支付能力。
 */
@Spi({"epay"})
public class EpayTencentWechatPayGateway extends DefaultTencentWechatPayGateway {
}
