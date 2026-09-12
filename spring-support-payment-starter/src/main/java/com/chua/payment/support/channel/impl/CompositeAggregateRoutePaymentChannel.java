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
 * 直营网关托管路由
 * @author CH
 * @since 4.0.0
 */
public class CompositeAggregateRoutePaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;
    /** Payment通道注册表 */
    private final PaymentChannelRegistry paymentChannelRegistry;

    /**
      * 创建 compositeaggregateroute薪酬通道 实例
     * @param merchantChannelService merchant通道服务
     * @param objectMapper 对象映射器
     * @param merchantChannelMapper merchant通道映射器
     * @param paymentChannelRegistry 薪酬通道registry
     */
    public CompositeAggregateRoutePaymentChannel(MerchantChannelService merchantChannelService,
                                                 ObjectMapper objectMapper,
                                                 MerchantChannelMapper merchantChannelMapper,
                                                 PaymentChannelRegistry paymentChannelRegistry) {
        /**
         * 父。
         * @param merchantChannelService merchant通道服务
         * @param objectMapper 对象映射器
         */
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentChannelRegistry = paymentChannelRegistry;
    }

    @Override
    /** 支持 */
    public boolean supports(String channelType, String channelSubType) {
        return "COMPOSITE".equalsIgnoreCase(channelType) && "AGGREGATE_ROUTE".equalsIgnoreCase(channelSubType);
    }

    @Override
    /** 创建订单 */
    public OrderCreateResult createOrder(MerchantChannel channel, OrderCreateRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).createOrder(targetChannel, request);
    }

    @Override
    /** 薪酬 */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).pay(targetChannel, request);
    }

    @Override
    /** 查询 */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).query(targetChannel, orderNo);
    }

    @Override
    /** 关闭 */
    public boolean close(MerchantChannel channel, String orderNo) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).close(targetChannel, orderNo);
    }

    @Override
    /** 退税 */
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).refund(targetChannel, request);
    }

    @Override
    /** 查询退税 */
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        MerchantChannel targetChannel = resolveTargetChannel(channel);
        return delegate(targetChannel).queryRefund(targetChannel, request);
    }

    /**
     * Delegate
     *
     * @param targetChannel Target通道
     * @return delegate的结果
     */
    private PaymentChannel delegate(MerchantChannel targetChannel) {
        return paymentChannelRegistry.getChannel(targetChannel.getChannelType(), targetChannel.getChannelSubType());
    }

    /**
     * 解析Target通道
     *
     * @param compositeChannel composite通道
     * @return resolveTarget通道的结果
     */
    private MerchantChannel resolveTargetChannel(MerchantChannel compositeChannel) {
        if (compositeChannel == null) {
            /**
             * 薪酬异常。
             * @param "直营网关渠道不存在" "直营网关渠道不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关渠道不存在");
        }
        Long targetChannelId = parseRouteChannelId(extConfig(compositeChannel));
        if (targetChannelId == null) {
            /**
             * 薪酬异常。
             * @param defaultChannelId" 默认通道标识"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置未提供 targetChannelId 或 defaultChannelId");
        }
        if (compositeChannel.getId() != null && compositeChannel.getId().equals(targetChannelId)) {
            /**
             * 薪酬异常。
             * @param "直营网关配置不能指向自身" "直营网关配置不能指向自身"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置不能指向自身");
        }
        MerchantChannel targetChannel = merchantChannelMapper.selectOne(new LambdaQueryWrapper<MerchantChannel>()
                .eq(MerchantChannel::getId, targetChannelId)
                .last("limit 1"));
        if (targetChannel == null) {
            /**
             * 薪酬异常。
             * @param targetChannelId Target通道标识
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关目标渠道不存在: " + targetChannelId);
        }
        if (compositeChannel.getMerchantId() != null && !compositeChannel.getMerchantId().equals(targetChannel.getMerchantId())) {
            /**
             * 薪酬异常。
             * @param "直营网关目标渠道和商户不匹配" "直营网关目标渠道和商户不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关目标渠道和商户不匹配");
        }
        if ("COMPOSITE".equalsIgnoreCase(targetChannel.getChannelType())) {
            /**
             * 薪酬异常。
             * @param 渠道" 渠道"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置不能嵌套 COMPOSITE 渠道");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(targetChannel.getStatus())) {
            /**
             * 薪酬异常。
             * @param "直营网关目标渠道未启用" "直营网关目标渠道未启用"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关目标渠道未启用");
        }
        if (!paymentChannelRegistry.supports(targetChannel.getChannelType(), targetChannel.getChannelSubType())) {
            throw new PaymentException("直营网关目标渠道不可执行: "
                    + targetChannel.getChannelType() + "/" + targetChannel.getChannelSubType());
        }
        return targetChannel;
    }

    /**
      * 解析route通道标识
     *
     * @param extConfig ext配置
     * @return 解析route通道id的结果
     */
    private Long parseRouteChannelId(Map<String, Object> extConfig) {
        return parseLong(extConfig.get("targetChannelId"), extConfig.get("defaultChannelId"));
    }

    /**
     * 解析Long
     *
     * @param values 价值观
     * @return 解析long的结果
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
                /**
                 * 薪酬异常。
                 * @param text 文本
                 * @param e e
                 * @return 薪酬异常的结果
                 */
                throw new PaymentException("直营网关 channelId 格式错误: " + text, e);
            }
        }
        return null;
    }
}

