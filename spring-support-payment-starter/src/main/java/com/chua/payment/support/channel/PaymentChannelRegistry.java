package com.chua.payment.support.channel;

import com.chua.payment.support.exception.PaymentException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 支付渠道注册表
 */
@Component
public class PaymentChannelRegistry {

    private final ObjectProvider<PaymentChannel> channels;

    public PaymentChannelRegistry(ObjectProvider<PaymentChannel> channels) {
        this.channels = channels;
    }

    public PaymentChannel getChannel(String channelType, String channelSubType) {
        return channels().stream()
                .filter(channel -> channel.supports(channelType, channelSubType))
                .findFirst()
                .orElseThrow(() -> new PaymentException("暂不支持的支付方式: "
                        + channelType
                        + (StringUtils.hasText(channelSubType) ? "/" + channelSubType : "")));
    }

    public boolean supports(String channelType, String channelSubType) {
        return channels().stream().anyMatch(channel -> channel.supports(channelType, channelSubType));
    }

    private List<PaymentChannel> channels() {
        return channels.orderedStream().toList();
    }
}
