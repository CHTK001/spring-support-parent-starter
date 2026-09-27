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
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 运营商关联路由通道
 * @author CH
 * @since 4.0.0
 */
public class CompositeAggregateRoutePaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    /**
     * merchant 通道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 支付通道注册表
     */
    private final PaymentChannelRegistry paymentChannelRegistry;

    /**
     * 创建运营商关联路由支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param merchantChannelMapper merchant 通道映射器
     * @param paymentChannelRegistry 支付通道注册表
     */
    public CompositeAggregateRoutePaymentChannel(MerchantChannelService merchantChannelService,
                                                 ObjectMapper objectMapper,
                                                 MerchantChannelMapper merchantChannelMapper,
                                                 PaymentChannelRegistry paymentChannelRegistry) {
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentChannelRegistry = paymentChannelRegistry;
    }

    @Override
    /**
     * 是否支持该通道
     */
    public boolean supports(String channelType, String channelSubType) {
        return "COMPOSITE".equalsIgnoreCase(channelType) && "AGGREGATE_ROUTE".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 创建订单
     */
    public OrderCreateResult createOrder(MerchantChannel channel, OrderCreateRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).createOrder(targetChannel, request);
    }

    @Override
    /**
     * 发起支付
     */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).pay(targetChannel, request);
    }

    @Override
    /**
     * 查询支付结果
     */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).query(targetChannel, orderNo);
    }

    @Override
    /**
     * 关闭订单
     */
    public boolean close(MerchantChannel channel, String orderNo) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).close(targetChannel, orderNo);
    }

    @Override
    /**
     * 申请退款
     */
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).refund(targetChannel, request);
    }

    @Override
    /**
     * 查询退款结果
     */
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).queryRefund(targetChannel, request);
    }

    /**
     * 获取实际执行的支付通道
     *
     * @param targetChannel 目标通道配置
     * @return 委派的支付通道
     */
    private PaymentChannel delegate(MerchantChannel targetChannel) {
        return paymentChannelRegistry.getChannel(targetChannel.getChannelType(), targetChannel.getChannelSubType());
    }

    /**
     * 解析目标通道
     *
     * @param compositeChannel 运营商关联通道配置
     * @return 解析出的目标通道
     */
    private MerchantChannel resolveTargetChannel(MerchantChannel compositeChannel) {
        if (compositeChannel == null) {
            throw new PaymentException("运营商关联网道不存在");
        }
        Long targetChannelId = parseRouteChannelId(extConfig(compositeChannel));
        if (targetChannelId == null) {
            throw new PaymentException("运营商关联配置未提供 targetChannelId 或 defaultChannelId");
        }
        if (compositeChannel.getId() != null && compositeChannel.getId().equals(targetChannelId)) {
            throw new PaymentException("运营商关联配置不能指向自身");
        }
        MerchantChannel targetChannel = merchantChannelMapper.selectOne(new LambdaQueryWrapper<MerchantChannel>()
                .eq(MerchantChannel::getId, targetChannelId)
                .last("limit 1"));
        if (targetChannel == null) {
            throw new PaymentException("运营商关联目标通道不存在: " + targetChannelId);
        }
        if (compositeChannel.getMerchantId() != null && !compositeChannel.getMerchantId().equals(targetChannel.getMerchantId())) {
            throw new PaymentException("运营商关联目标通道和商户不匹配");
        }
        if ("COMPOSITE".equalsIgnoreCase(targetChannel.getChannelType())) {
            throw new PaymentException("运营商关联配置不能嵌套 COMPOSITE 通道");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(targetChannel.getStatus())) {
            throw new PaymentException("运营商关联目标通道未启用");
        }
        if (!paymentChannelRegistry.supports(targetChannel.getChannelType(), targetChannel.getChannelSubType())) {
            throw new PaymentException("运营商关联目标通道不可执行: "
                    + targetChannel.getChannelType() + "/" + targetChannel.getChannelSubType());
        }
        return targetChannel;
    }

    /**
     * 解析路由通道标识
     *
     * @param extConfig ext 配置
     * @return 解析出的路由通道 ID
     */
    private Long parseRouteChannelId(Map<String, Object> extConfig) {
        return parseLong(extConfig.get("targetChannelId"), extConfig.get("defaultChannelId"));
    }

    /**
     * 解析 Long 值
     *
     * @param values 候选值
     * @return 解析出的 Long 值
     */
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
                throw new PaymentException("运营商关联 channelId 格式错误: " + text, e);
            }
        }
        return null;
    }
}
