package com.chua.payment.support.channel.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.channel.OrderCreateRequest;
import com.chua.payment.support.channel.OrderCreateResult;
import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentChannelRegistry;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.service.MerchantChannelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 直营网关托管路由
 */
@Component
public class CompositeAggregateRoutePaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    private final MerchantChannelMapper merchantChannelMapper;
    private final PaymentChannelRegistry paymentChannelRegistry;

    public CompositeAggregateRoutePaymentChannel(MerchantChannelService merchantChannelService,
                                                 ObjectMapper objectMapper,
                                                 MerchantChannelMapper merchantChannelMapper,
                                                 PaymentChannelRegistry paymentChannelRegistry) {
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentChannelRegistry = paymentChannelRegistry;
    }

    @Override
    public boolean supports(String channelType, String channelSubType) {
        return "COMPOSITE".equalsIgnoreCase(channelType) && "AGGREGATE_ROUTE".equalsIgnoreCase(channelSubType);
    }

    @Override
    public OrderCreateResult createOrder(MerchantChannel channel, OrderCreateRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).createOrder(targetChannel, request);
    }

    @Override
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).pay(targetChannel, request);
    }

    @Override
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).query(targetChannel, orderNo);
    }

    @Override
    public boolean close(MerchantChannel channel, String orderNo) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).close(targetChannel, orderNo);
    }

    @Override
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).refund(targetChannel, request);
    }

    @Override
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).queryRefund(targetChannel, request);
    }

    private PaymentChannel delegate(MerchantChannel targetChannel) {
        return paymentChannelRegistry.getChannel(targetChannel.getChannelType(), targetChannel.getChannelSubType());
    }

    private MerchantChannel resolveTargetChannel(MerchantChannel compositeChannel) {
        if (compositeChannel == null) {
            throw new PaymentException("直营网关渠道不存在");
        }
        Long targetChannelId = parseRouteChannelId(extConfig(compositeChannel));
        if (targetChannelId == null) {
            throw new PaymentException("直营网关配置未提供 targetChannelId 或 defaultChannelId");
        }
        if (compositeChannel.getId() != null && compositeChannel.getId().equals(targetChannelId)) {
            throw new PaymentException("直营网关配置不能指向自身");
        }
        MerchantChannel targetChannel = merchantChannelMapper.selectOne(new LambdaQueryWrapper<MerchantChannel>()
                .eq(MerchantChannel::getId, targetChannelId)
                .last("limit 1"));
        if (targetChannel == null) {
            throw new PaymentException("直营网关目标渠道不存在: " + targetChannelId);
        }
        if (compositeChannel.getMerchantId() != null && !compositeChannel.getMerchantId().equals(targetChannel.getMerchantId())) {
            throw new PaymentException("直营网关目标渠道和商户不匹配");
        }
        if ("COMPOSITE".equalsIgnoreCase(targetChannel.getChannelType())) {
            throw new PaymentException("直营网关配置不能嵌套 COMPOSITE 渠道");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(targetChannel.getStatus())) {
            throw new PaymentException("直营网关目标渠道未启用");
        }
        if (!paymentChannelRegistry.supports(targetChannel.getChannelType(), targetChannel.getChannelSubType())) {
            throw new PaymentException("直营网关目标渠道不可执行: "
                    + targetChannel.getChannelType() + "/" + targetChannel.getChannelSubType());
        }
        return targetChannel;
    }

    private Long parseRouteChannelId(Map<String, Object> extConfig) {
        return parseLong(extConfig.get("targetChannelId"), extConfig.get("defaultChannelId"));
    }

    private Long parseLong(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new PaymentException("直营网关 channelId 格式错误: " + text, e);
            }
        }
        return null;
    }
}
