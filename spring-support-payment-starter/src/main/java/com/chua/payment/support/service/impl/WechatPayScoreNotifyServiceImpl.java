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

    /** Merchant通道mapper */
    private final MerchantChannelMapper merchantChannelMapper;
    /** 提供者gateway注册表 */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    /** WechatPAY分数排序服务 */
    private final WechatPayScoreOrderService wechatPayScoreOrderService;

    /**
     * 创建 WechatPayScoreNotifyServiceImpl 实例
     * @param merchantChannelService merchantChannelService
     * @param objectMapper objectMapper
     * @param merchantChannelMapper merchantChannelMapper
     * @param providerGatewayRegistry providerGatewayRegistry
     * @param wechatPayScoreOrderService wechatPayScoreOrderService
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
     * 处理通知
     * @param channelId channelId
     * @param outOrderNo outOrderNo
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     */
    public TencentWechatPayScoreNotifyPayload handleNotify(WechatPayScoreNotifyDTO dto) {
        MerchantChannel channel = requireEnabledWechatChannel(dto.channelId());
        TencentWechatPayScoreNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayScoreNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));
        if (StringUtils.hasText(dto.outOrderNo()) && StringUtils.hasText(payload.getOutOrderNo()) && !dto.outOrderNo().equals(payload.getOutOrderNo())) {
            throw new PaymentException("微信支付分回调路径订单号和报文订单号不一致");
        }
        wechatPayScoreOrderService.handleNotify(dto.channelId(), dto.outOrderNo(), payload, dto.body());
        return payload;
    }

    /** RequireEnabledWechatChannel */
    private MerchantChannel requireEnabledWechatChannel(Long channelId) {
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new PaymentException("支付方式不存在");
        }
        if (!"WECHAT".equalsIgnoreCase(channel.getChannelType())) {
            throw new PaymentException("支付方式类型不匹配");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    /**
     * 构建Wechat通知Request
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     */
    private TencentWechatNotifyRequest buildWechatNotifyRequest(String serialNumber,
                                                                String timestamp,
                                                                String nonce,
                                                                String signature,
                                                                String signType,
                                                                String body) {
        TencentWechatNotifyRequest request = new TencentWechatNotifyRequest();
        request.setSerialNumber(serialNumber);
        request.setTimestamp(timestamp);
        request.setNonce(nonce);
        request.setSignature(signature);
        request.setSignType(signType);
        request.setBody(body);
        return request;
    }

    /** 构建WechatProperties */
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

