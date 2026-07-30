package com.chua.payment.support.service.impl;

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
 * 微信支付分回调服务实现
 */
@Service
public class WechatPayScoreNotifyServiceImpl extends AbstractMerchantPaymentChannel implements WechatPayScoreNotifyService {

    private final MerchantChannelMapper merchantChannelMapper;
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    private final WechatPayScoreOrderService wechatPayScoreOrderService;

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
    public TencentWechatPayScoreNotifyPayload handleNotify(Long channelId,
                                                           String outOrderNo,
                                                           String serialNumber,
                                                           String timestamp,
                                                           String nonce,
                                                           String signature,
                                                           String signType,
                                                           String body) {
        MerchantChannel channel = requireEnabledWechatChannel(channelId);
        TencentWechatPayScoreNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayScoreNotify(buildWechatProperties(channel), buildWechatNotifyRequest(serialNumber, timestamp, nonce, signature, signType, body));
        if (StringUtils.hasText(outOrderNo) && StringUtils.hasText(payload.getOutOrderNo()) && !outOrderNo.equals(payload.getOutOrderNo())) {
            throw new PaymentException("微信支付分回调路径订单号和报文订单号不一致");
        }
        wechatPayScoreOrderService.handleNotify(channelId, outOrderNo, payload, body);
        return payload;
    }

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
