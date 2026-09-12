package com.chua.payment.support.channel;

import com.chua.payment.support.exception.PaymentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
* 支付渠道注册表
* @author CH
* @since 4.0.0
 */
@Component
public class PaymentChannelRegistry {

    private volatile List<PaymentChannel> channels; // 通道

    @Autowired
    /** Application上下文 */
    private ApplicationContext applicationContext;

    /**
    * 通道
    *
    * @return 通道的结果
     */
    private List<PaymentChannel> channels() {
        if (channels == null) {
            synchronized (this) {
                if (channels == null) {
                    channels = new ArrayList<>(applicationContext.getBeansOfType(PaymentChannel.class).values());
                }
            }
        }
        return channels;
    }

    /**
    * 测试支持：注入测试渠道列表
    * @param channels 通道
     */
    void setTestChannels(List<PaymentChannel> channels) {
        this.channels = channels;
    }

    /**
    * 获取通道
    *
    * @param channelType 通道类型
    * @param channelSubType 通道sub类型
    * @return 获取通道的结果
     */
    public PaymentChannel getChannel(String channelType, String channelSubType) {
        return channels().stream()
                .filter(channel -> channel.supports(channelType, channelSubType))
                .findFirst()
                .orElseThrow(() -> new PaymentException("暂不支持的支付方式: "
                        + channelType
                        + (StringUtils.hasText(channelSubType) ? "/" + channelSubType : "")
                        + ", 已注册渠道数: " + channels().size()));
    }

    /**
    * 支持
    *
    * @param channelType 通道类型
    * @param channelSubType 通道sub类型
    * @return 支持的结果
     */
    public boolean supports(String channelType, String channelSubType) {
        return channels().stream().anyMatch(channel -> channel.supports(channelType, channelSubType));
    }
}

