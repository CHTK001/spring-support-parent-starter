package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.WechatPayScoreNotifyDTO;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.WechatPayScoreOrderService;
import com.chua.payment.support.service.WechatPayScoreNotifyService;
import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 微信支付分回调服务实现，处理微信支付分异步通知并触发订单状态变更。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
public class WechatPayScoreNotifyServiceImpl extends AbstractMerchantPaymentChannel implements WechatPayScoreNotifyService {

    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 支付渠道网关注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    /**
     * 微信支付分订单服务
     */
    private final WechatPayScoreOrderService wechatPayScoreOrderService;

    /**
     * 构造微信支付分回调服务。
     * @param merchantChannelService 商户渠道服务
     * @param objectMapper 对象映射器
     * @param merchantChannelMapper 商户渠道映射器
     * @param providerGatewayRegistry 支付渠道网关注册表
     * @param wechatPayScoreOrderService 微信支付分订单服务
     */
    public WechatPayScoreNotifyServiceImpl(MerchantChannelService merchantChannelService,
                                           ObjectMapper objectMapper,
                                           MerchantChannelMapper merchantChannelMapper,
                                           PaymentProviderGatewayRegistry providerGatewayRegistry,
                                           WechatPayScoreOrderService wechatPayScoreOrderService) {
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.providerGatewayRegistry = providerGatewayRegistry;
        this.wechatPayScoreOrderService = wechatPayScoreOrderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理微信支付分通知
     * @param channelId 渠道标识
     * @param outOrderNo 出订单号
     * @param serialNumber 平台证书序列号
     * @param timestamp 时间戳
     * @param nonce 随机字符串
     * @param signature 签名值
     * @param signType 签名类型
     * @param body 请求主体
     * @param dto dto
     * @return 解析后的通知报文
     */
    public TencentWechatPayScoreNotifyPayload handleNotify(WechatPayScoreNotifyDTO dto) {
        MerchantChannel channel = requireEnabledWechatChannel(dto.channelId());
        TencentWechatPayScoreNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayScoreNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));
        if (StringUtils.hasText(dto.outOrderNo()) && StringUtils.hasText(payload.getOutOrderNo()) && !dto.outOrderNo().equals(payload.getOutOrderNo())) {
            throw new PaymentException("微信支付分回调路径出订单号和报文出订单号不一致");
        }
        wechatPayScoreOrderService.handleNotify(dto.channelId(), dto.outOrderNo(), payload, dto.body());
        return payload;
    }

    /**
     * 获取已启用的微信渠道
     *
     * @param channelId 渠道标识
     * @return 获取到的已启用微信渠道
     */
    private MerchantChannel requireEnabledWechatChannel(Long channelId) {
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new PaymentException("支付方式不存在");
        }
        if (!"WECHAT".equalsIgnoreCase(channel.getChannelType())) {
            throw new PaymentException("支付方式类型不匹配");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    /**
     * 构建微信通知请求对象
     * @param serialNumber 平台证书序列号
     * @param timestamp 时间戳
     * @param nonce 随机字符串
     * @param signature 签名值
     * @param signType 签名类型
     * @param body 请求主体
     */
    private TencentWechatNotifyRequest buildWechatNotifyRequest(String serialNumber,
                                                                String timestamp,
                                                                String nonce,
                                                                String signature,
                                                                String signType,
                                                                String body) {
        TencentWechatNotifyRequest request = new TencentWechatNotifyRequest(
                serialNumber,
                timestamp,
                nonce,
                signature,
                signType,
                body
        );
        return request;
    }

    /**
     * 构建微信支付配置属性
     *
     * @param channel 渠道
     * @return 构建出的微信支付配置属性
     */
    private TencentWechatPayProperties buildWechatProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(extText(channel, "merchantSerialNumber"), "微信商户证书序列号不能为空"));
        properties.setNotifyUrl(channel.getNotifyUrl());
        return properties;
    }
}
