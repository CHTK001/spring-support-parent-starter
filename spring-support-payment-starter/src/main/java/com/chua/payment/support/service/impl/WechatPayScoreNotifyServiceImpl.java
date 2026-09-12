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

    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;
    /** 提供者gateway注册表 */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    /** wechat薪酬分数排序服务 */
    private final WechatPayScoreOrderService wechatPayScoreOrderService;

    /**
    * 创建 wechat薪酬score通知服务impl 实例
    * @param merchantChannelService merchant通道服务
    * @param objectMapper 对象映射器
    * @param merchantChannelMapper merchant通道映射器
    * @param providerGatewayRegistry 提供者gatewayregistry
    * @param wechatPayScoreOrderService wechat薪酬score订单服务
     */
    public WechatPayScoreNotifyServiceImpl(MerchantChannelService merchantChannelService,
                                           ObjectMapper objectMapper,
                                           MerchantChannelMapper merchantChannelMapper,
                                           PaymentProviderGatewayRegistry providerGatewayRegistry,
                                           WechatPayScoreOrderService wechatPayScoreOrderService) {
        /**
        * 父。
        * @param merchantChannelService merchant通道服务
        * @param objectMapper 对象映射器
         */
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.providerGatewayRegistry = providerGatewayRegistry;
        this.wechatPayScoreOrderService = wechatPayScoreOrderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 处理通知
    * @param channelId 通道标识
    * @param outOrderNo 出订单no
    * @param serialNumber 串行数字
    * @param timestamp 时间戳
    * @param nonce nonce
    * @param signature 签名
    * @param signType 标志类型
    * @param body 主体
    * @param dto dto
    * @return 把手通知的结果
     */
    public TencentWechatPayScoreNotifyPayload handleNotify(WechatPayScoreNotifyDTO dto) {
        MerchantChannel channel = requireEnabledWechatChannel(dto.channelId());
        TencentWechatPayScoreNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayScoreNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));
        if (StringUtils.hasText(dto.outOrderNo()) && StringUtils.hasText(payload.getOutOrderNo()) && !dto.outOrderNo().equals(payload.getOutOrderNo())) {
            /**
            * 薪酬异常。
            * @param "微信支付分回调路径订单号和报文订单号不一致" "微信支付分回调路径订单号和报文订单号不一致"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("微信支付分回调路径订单号和报文订单号不一致");
        }
        wechatPayScoreOrderService.handleNotify(dto.channelId(), dto.outOrderNo(), payload, dto.body());
        return payload;
    }

    /**
    * require已启用wechat通道
    *
    * @param channelId 通道标识
    * @return require已启用wechat通道的结果
     */
    private MerchantChannel requireEnabledWechatChannel(Long channelId) {
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            /**
            * 薪酬异常。
            * @param "支付方式不存在" "支付方式不存在"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式不存在");
        }
        if (!"WECHAT".equalsIgnoreCase(channel.getChannelType())) {
            /**
            * 薪酬异常。
            * @param "支付方式类型不匹配" "支付方式类型不匹配"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式类型不匹配");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            /**
            * 薪酬异常。
            * @param "支付方式未启用" "支付方式未启用"
            * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    /**
    * 构建Wechat通知请求
    * @param serialNumber 串行数字
    * @param timestamp 时间戳
    * @param nonce nonce
    * @param signature 签名
    * @param signType 标志类型
    * @param body 主体
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

    /**
    * 构建wechat属性
    *
    * @param channel 通道
    * @return 构建wechat属性的结果
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

