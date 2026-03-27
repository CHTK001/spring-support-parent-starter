package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.WechatPayScoreOrderMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.PaymentCallbackUrlResolver;
import com.chua.payment.support.service.WechatPayScoreOrderService;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 微信支付分订单服务实现
 */
@Service
public class WechatPayScoreOrderServiceImpl extends AbstractMerchantPaymentChannel implements WechatPayScoreOrderService {

    private final WechatPayScoreOrderMapper wechatPayScoreOrderMapper;
    private final MerchantChannelMapper merchantChannelMapper;
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;

    public WechatPayScoreOrderServiceImpl(MerchantChannelService merchantChannelService,
                                          ObjectMapper objectMapper,
                                          WechatPayScoreOrderMapper wechatPayScoreOrderMapper,
                                          MerchantChannelMapper merchantChannelMapper,
                                          PaymentProviderGatewayRegistry providerGatewayRegistry,
                                          PaymentCallbackUrlResolver paymentCallbackUrlResolver) {
        super(merchantChannelService, objectMapper);
        this.wechatPayScoreOrderMapper = wechatPayScoreOrderMapper;
        this.merchantChannelMapper = merchantChannelMapper;
        this.providerGatewayRegistry = providerGatewayRegistry;
        this.paymentCallbackUrlResolver = paymentCallbackUrlResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatPayScoreOrder createOrder(WechatPayScoreCreateDTO request) {
        if (request == null) {
            throw new PaymentException("微信支付分下单请求不能为空");
        }
        MerchantChannel channel = requireEnabledWechatChannel(request.getChannelId());
        if (request.getMerchantId() != null && !request.getMerchantId().equals(channel.getMerchantId())) {
            throw new PaymentException("微信支付分商户和渠道归属不一致");
        }
        String outOrderNo = StringUtils.hasText(request.getOutOrderNo()) ? request.getOutOrderNo() : generateOutOrderNo();
        WechatPayScoreOrder existing = getByOutOrderNo(outOrderNo);
        if (existing != null) {
            return existing;
        }

        TencentWechatPayScoreRequest gatewayRequest = new TencentWechatPayScoreRequest();
        gatewayRequest.setAppId(channel.getAppId());
        gatewayRequest.setServiceId(resolveServiceId(channel, request.getServiceId()));
        gatewayRequest.setOutOrderNo(outOrderNo);
        gatewayRequest.setOpenId(requiredText(request.getOpenId(), "微信支付分 openId 不能为空"));
        gatewayRequest.setNotifyUrl(requiredText(firstNonBlank(
                request.getNotifyUrl(),
                extText(channel, "payScoreNotifyUrl"),
                extText(channel, "paymentPointNotifyUrl"),
                paymentCallbackUrlResolver.defaultWechatPayScoreNotifyUrl(channel.getId(), outOrderNo)),
                "微信支付分回调地址不能为空"));
        gatewayRequest.setServiceIntroduction(firstNonBlank(request.getServiceIntroduction(), extText(channel, "payScoreServiceName"), "微信支付分服务"));
        gatewayRequest.setStartTime(request.getStartTime());
        gatewayRequest.setEndTime(request.getEndTime());
        gatewayRequest.setTotalAmountFen(amountToFen(request.getTotalAmount()));
        gatewayRequest.setReason(request.getReason());
        gatewayRequest.setFinishType(request.getFinishType());
        gatewayRequest.setNeedUserConfirm(request.getNeedUserConfirm());
        gatewayRequest.setAttach(request.getAttach());
        gatewayRequest.setPostPayments(request.getPostPayments());
        gatewayRequest.setPostDiscounts(request.getPostDiscounts());
        gatewayRequest.setExtraParams(request.getExtraParams());

        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .createPayScoreOrder(buildWechatProperties(channel), gatewayRequest);

        WechatPayScoreOrder order = new WechatPayScoreOrder();
        order.setMerchantId(request.getMerchantId() != null ? request.getMerchantId() : channel.getMerchantId());
        order.setChannelId(channel.getId());
        order.setUserId(request.getUserId());
        order.setOutOrderNo(outOrderNo);
        order.setServiceOrderNo(response.getServiceOrderNo());
        order.setAppId(firstNonBlank(channel.getAppId(), gatewayRequest.getAppId()));
        order.setServiceId(gatewayRequest.getServiceId());
        order.setOpenId(gatewayRequest.getOpenId());
        order.setState(firstNonBlank(response.getState(), "CREATED"));
        order.setTotalAmount(request.getTotalAmount());
        order.setNotifyUrl(gatewayRequest.getNotifyUrl());
        order.setServiceIntroduction(gatewayRequest.getServiceIntroduction());
        order.setStartTime(gatewayRequest.getStartTime());
        order.setEndTime(gatewayRequest.getEndTime());
        order.setFinishType(gatewayRequest.getFinishType());
        order.setReason(gatewayRequest.getReason());
        order.setPackageInfo(response.getPackageInfo());
        order.setAttach(gatewayRequest.getAttach());
        order.setRequestPayload(toJson(request));
        order.setResponsePayload(firstNonBlank(response.getRawResponse(), toJson(response.getRawData())));
        order.setRemark(request.getRemark());
        updateTerminalState(order);
        wechatPayScoreOrderMapper.insert(order);
        return order;
    }

    @Override
    public WechatPayScoreOrder getByOutOrderNo(String outOrderNo) {
        if (!StringUtils.hasText(outOrderNo)) {
            return null;
        }
        return wechatPayScoreOrderMapper.selectOne(new LambdaQueryWrapper<WechatPayScoreOrder>()
                .eq(WechatPayScoreOrder::getOutOrderNo, outOrderNo)
                .last("limit 1"));
    }

    @Override
    public Page<WechatPayScoreOrder> page(int pageNum, int pageSize, Long merchantId, Long channelId, String openId, String state) {
        LambdaQueryWrapper<WechatPayScoreOrder> wrapper = new LambdaQueryWrapper<WechatPayScoreOrder>()
                .orderByDesc(WechatPayScoreOrder::getCreateTime);
        if (merchantId != null) {
            wrapper.eq(WechatPayScoreOrder::getMerchantId, merchantId);
        }
        if (channelId != null) {
            wrapper.eq(WechatPayScoreOrder::getChannelId, channelId);
        }
        if (StringUtils.hasText(openId)) {
            wrapper.eq(WechatPayScoreOrder::getOpenId, openId);
        }
        if (StringUtils.hasText(state)) {
            wrapper.eq(WechatPayScoreOrder::getState, state);
        }
        return wechatPayScoreOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatPayScoreOrder syncOrder(String outOrderNo) {
        WechatPayScoreOrder order = requireOrder(outOrderNo);
        MerchantChannel channel = requireEnabledWechatChannel(order.getChannelId());
        TencentWechatPayScoreRequest request = baseRequest(order);
        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .queryPayScoreOrder(buildWechatProperties(channel), request);
        mergeResponse(order, response);
        wechatPayScoreOrderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatPayScoreOrder completeOrder(String outOrderNo, WechatPayScoreCompleteDTO request) {
        WechatPayScoreOrder order = requireOrder(outOrderNo);
        MerchantChannel channel = requireEnabledWechatChannel(order.getChannelId());
        WechatPayScoreCompleteDTO actualRequest = request == null ? new WechatPayScoreCompleteDTO() : request;
        TencentWechatPayScoreRequest gatewayRequest = baseRequest(order);
        gatewayRequest.setFinishType(firstNonBlank(actualRequest.getFinishType(), order.getFinishType(), "FINISH"));
        gatewayRequest.setReason(firstNonBlank(actualRequest.getReason(), order.getReason(), "服务已完成"));
        gatewayRequest.setEndTime(firstNonBlank(actualRequest.getEndTime(), order.getEndTime()));
        gatewayRequest.setTotalAmountFen(amountToFen(actualRequest.getTotalAmount() != null ? actualRequest.getTotalAmount() : order.getTotalAmount()));
        gatewayRequest.setPostPayments(actualRequest.getPostPayments());
        gatewayRequest.setPostDiscounts(actualRequest.getPostDiscounts());
        gatewayRequest.setExtraParams(actualRequest.getExtraParams());

        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .completePayScoreOrder(buildWechatProperties(channel), gatewayRequest);
        if (actualRequest.getTotalAmount() != null) {
            order.setTotalAmount(actualRequest.getTotalAmount());
        }
        order.setFinishType(gatewayRequest.getFinishType());
        order.setReason(gatewayRequest.getReason());
        order.setEndTime(gatewayRequest.getEndTime());
        if (StringUtils.hasText(actualRequest.getRemark())) {
            order.setRemark(actualRequest.getRemark());
        }
        mergeResponse(order, response);
        wechatPayScoreOrderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatPayScoreOrder cancelOrder(String outOrderNo, WechatPayScoreCancelDTO request) {
        WechatPayScoreOrder order = requireOrder(outOrderNo);
        MerchantChannel channel = requireEnabledWechatChannel(order.getChannelId());
        WechatPayScoreCancelDTO actualRequest = request == null ? new WechatPayScoreCancelDTO() : request;
        TencentWechatPayScoreRequest gatewayRequest = baseRequest(order);
        gatewayRequest.setReason(firstNonBlank(actualRequest.getReason(), order.getReason(), "商户取消"));
        gatewayRequest.setExtraParams(actualRequest.getExtraParams());

        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .cancelPayScoreOrder(buildWechatProperties(channel), gatewayRequest);
        order.setReason(gatewayRequest.getReason());
        if (StringUtils.hasText(actualRequest.getRemark())) {
            order.setRemark(actualRequest.getRemark());
        }
        mergeResponse(order, response);
        wechatPayScoreOrderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(Long channelId, String outOrderNo, TencentWechatPayScoreNotifyPayload payload, String responsePayload) {
        if (payload == null) {
            throw new PaymentException("微信支付分回调内容不能为空");
        }
        if (StringUtils.hasText(outOrderNo) && StringUtils.hasText(payload.getOutOrderNo()) && !outOrderNo.equals(payload.getOutOrderNo())) {
            throw new PaymentException("微信支付分回调路径订单号和报文订单号不一致");
        }
        WechatPayScoreOrder order = requireOrder(firstNonBlank(payload.getOutOrderNo(), outOrderNo));
        if (channelId != null && order.getChannelId() != null && !channelId.equals(order.getChannelId())) {
            throw new PaymentException("微信支付分回调渠道与订单渠道不一致");
        }
        order.setServiceOrderNo(firstNonBlank(payload.getServiceOrderNo(), order.getServiceOrderNo()));
        order.setAppId(firstNonBlank(payload.getAppId(), order.getAppId()));
        order.setServiceId(firstNonBlank(payload.getServiceId(), order.getServiceId()));
        order.setOpenId(firstNonBlank(payload.getOpenId(), order.getOpenId()));
        order.setState(firstNonBlank(payload.getState(), order.getState()));
        order.setFinishReason(firstNonBlank(payload.getFinishReason(), order.getFinishReason()));
        order.setResponsePayload(firstNonBlank(responsePayload, toJson(payload.getRawData()), order.getResponsePayload()));
        updateTerminalState(order);
        wechatPayScoreOrderMapper.updateById(order);
    }

    private WechatPayScoreOrder requireOrder(String outOrderNo) {
        WechatPayScoreOrder order = getByOutOrderNo(outOrderNo);
        if (order == null) {
            throw new PaymentException("微信支付分订单不存在: " + outOrderNo);
        }
        return order;
    }

    private MerchantChannel requireEnabledWechatChannel(Long channelId) {
        if (channelId == null) {
            throw new PaymentException("微信支付分 channelId 不能为空");
        }
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

    private TencentWechatPayProperties buildWechatProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(extText(channel, "merchantSerialNumber"), "微信商户证书序列号不能为空"));
        properties.setNotifyUrl(firstNonBlank(channel.getNotifyUrl(), extText(channel, "payScoreNotifyUrl")));
        return properties;
    }

    private TencentWechatPayScoreRequest baseRequest(WechatPayScoreOrder order) {
        TencentWechatPayScoreRequest request = new TencentWechatPayScoreRequest();
        request.setAppId(order.getAppId());
        request.setServiceId(order.getServiceId());
        request.setOutOrderNo(order.getOutOrderNo());
        request.setOpenId(order.getOpenId());
        request.setNotifyUrl(order.getNotifyUrl());
        request.setServiceIntroduction(order.getServiceIntroduction());
        request.setStartTime(order.getStartTime());
        request.setEndTime(order.getEndTime());
        request.setFinishType(order.getFinishType());
        request.setReason(order.getReason());
        request.setAttach(order.getAttach());
        request.setTotalAmountFen(amountToFen(order.getTotalAmount()));
        return request;
    }

    private void mergeResponse(WechatPayScoreOrder order, TencentWechatPayScoreResponse response) {
        order.setServiceOrderNo(firstNonBlank(response.getServiceOrderNo(), order.getServiceOrderNo()));
        order.setState(firstNonBlank(response.getState(), order.getState()));
        order.setPackageInfo(firstNonBlank(response.getPackageInfo(), order.getPackageInfo()));
        order.setResponsePayload(firstNonBlank(response.getRawResponse(), toJson(response.getRawData()), order.getResponsePayload()));
        updateTerminalState(order);
    }

    private void updateTerminalState(WechatPayScoreOrder order) {
        String state = upper(order.getState());
        if (!StringUtils.hasText(state)) {
            return;
        }
        if (state.contains("COMPLETE") || state.contains("SUCCESS") || state.contains("DONE") || state.contains("FINISH")) {
            if (order.getCompletedAt() == null) {
                order.setCompletedAt(LocalDateTime.now());
            }
            return;
        }
        if (state.contains("CANCEL") || state.contains("CLOSE")) {
            if (order.getCancelledAt() == null) {
                order.setCancelledAt(LocalDateTime.now());
            }
        }
    }

    private String resolveServiceId(MerchantChannel channel, String requestServiceId) {
        return requiredText(firstNonBlank(
                requestServiceId,
                extText(channel, "payScoreServiceId"),
                extText(channel, "serviceId"),
                extText(channel, "wechatPayScoreServiceId"),
                extText(channel, "payMerchantConfigWechatPaymentPointServiceId")),
                "微信支付分服务ID不能为空");
    }

    private Integer amountToFen(BigDecimal amount) {
        return amount == null ? null : (int) yuanToFen(amount);
    }

    private String generateOutOrderNo() {
        return "PS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
