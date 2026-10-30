package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.dto.WechatPayScoreOrderQueryRequest;
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
 * 微信支付分订单服务实现，管理微信支付分场景下的先享后付支付订单生命周期。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
public class WechatPayScoreOrderServiceImpl extends AbstractMerchantPaymentChannel implements WechatPayScoreOrderService {

    /**
     * 微信支付分订单映射器
     */
    private final WechatPayScoreOrderMapper wechatPayScoreOrderMapper;
    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 支付渠道网关注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    /**
     * 支付回调地址解析器
     */
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;

    /**
     * 构造微信支付分订单服务。
     * @param merchantChannelService 商户渠道服务
     * @param objectMapper 对象映射器
     * @param wechatPayScoreOrderMapper 微信支付分订单映射器
     * @param merchantChannelMapper 商户渠道映射器
     * @param providerGatewayRegistry 支付渠道网关注册表
     * @param paymentCallbackUrlResolver 支付回调地址解析器
     */
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
    /**
     * 创建支付分订单
     *
     * @param request 请求参数
     * @return 创建出的支付分订单
     */
    public WechatPayScoreOrder createOrder(WechatPayScoreCreateDTO request) {
        if (request == null) {
            throw new PaymentException("微信支付分下单请求不能为空");
        }
        MerchantChannel channel = requireEnabledWechatChannel(request.channelId());
        if (request.merchantId() != null && !request.merchantId().equals(channel.getMerchantId())) {
            throw new PaymentException("微信支付分商户和渠道归属不一致");
        }
        String outOrderNo = StringUtils.hasText(request.outOrderNo()) ? request.outOrderNo() : generateOutOrderNo();
        WechatPayScoreOrder existing = getByOutOrderNo(outOrderNo);
        if (existing != null) {
            return existing;
        }

        TencentWechatPayScoreRequest gatewayRequest = new TencentWechatPayScoreRequest(
                channel.getAppId(),
                resolveServiceId(channel, request.serviceId()),
                outOrderNo,
                requiredText(request.openId(), "微信支付分 openId 不能为空"),
                requiredText(firstNonBlank(
                        request.notifyUrl(),
                        extText(channel, "payScoreNotifyUrl"),
                        extText(channel, "paymentPointNotifyUrl"),
                        paymentCallbackUrlResolver.defaultWechatPayScoreNotifyUrl(channel.getId(), outOrderNo)),
                        "微信支付分回调地址不能为空"),
                firstNonBlank(request.serviceIntroduction(), extText(channel, "payScoreServiceName"), "微信支付分服务"),
                request.startTime(),
                request.endTime(),
                amountToFen(request.totalAmount()),
                request.reason(),
                request.finishType(),
                request.needUserConfirm(),
                request.attach(),
                request.postPayments(),
                request.postDiscounts(),
                request.extraParams());

        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .createPayScoreOrder(buildWechatProperties(channel), gatewayRequest);

        WechatPayScoreOrder order = new WechatPayScoreOrder();
        order.setMerchantId(request.merchantId() != null ? request.merchantId() : channel.getMerchantId());
        order.setChannelId(channel.getId());
        order.setUserId(request.userId());
        order.setOutOrderNo(outOrderNo);
        order.setServiceOrderNo(response.serviceOrderNo());
        order.setAppId(firstNonBlank(channel.getAppId(), gatewayRequest.appId()));
        order.setServiceId(gatewayRequest.serviceId());
        order.setOpenId(gatewayRequest.openId());
        order.setState(firstNonBlank(response.state(), "CREATED"));
        order.setTotalAmount(request.totalAmount());
        order.setNotifyUrl(gatewayRequest.notifyUrl());
        order.setServiceIntroduction(gatewayRequest.serviceIntroduction());
        order.setStartTime(gatewayRequest.startTime());
        order.setEndTime(gatewayRequest.endTime());
        order.setFinishType(gatewayRequest.finishType());
        order.setReason(gatewayRequest.reason());
        order.setPackageInfo(response.packageInfo());
        order.setAttach(gatewayRequest.attach());
        order.setRequestPayload(toJson(request));
        order.setResponsePayload(firstNonBlank(response.rawResponse(), toJson(response.rawData())));
        order.setRemark(request.remark());
        /**
         * 更新终态时间戳
         * @param order 支付分订单
         */
        updateTerminalState(order);
        wechatPayScoreOrderMapper.insert(order);
        return order;
    }

    @Override
    /**
     * 根据出订单号获取支付分订单
     * @param outOrderNo 字符串
     * @return 返回值
    */
    public WechatPayScoreOrder getByOutOrderNo(String outOrderNo) {
        if (!StringUtils.hasText(outOrderNo)) {
            return null;
        }
        return wechatPayScoreOrderMapper.selectOne(new LambdaQueryWrapper<WechatPayScoreOrder>()
                .eq(WechatPayScoreOrder::getOutOrderNo, outOrderNo)
                .last("limit 1"));
    }

    @Override
    public Page<WechatPayScoreOrder> page(WechatPayScoreOrderQueryRequest request) {
        if (request == null) {
            throw new PaymentException("查询参数不能为空");
        }
        LambdaQueryWrapper<WechatPayScoreOrder> wrapper = new LambdaQueryWrapper<WechatPayScoreOrder>()
                .orderByDesc(WechatPayScoreOrder::getCreateTime);
        if (request.merchantId() != null) {
            wrapper.eq(WechatPayScoreOrder::getMerchantId, request.merchantId());
        }
        if (request.channelId() != null) {
            wrapper.eq(WechatPayScoreOrder::getChannelId, request.channelId());
        }
        if (StringUtils.hasText(request.openId())) {
            wrapper.eq(WechatPayScoreOrder::getOpenId, request.openId());
        }
        if (StringUtils.hasText(request.state())) {
            wrapper.eq(WechatPayScoreOrder::getState, request.state());
        }
        return wechatPayScoreOrderMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 同步订单状态
     *
     * @param outOrderNo 出订单号
     * @return 同步后的支付分订单
     */
    public WechatPayScoreOrder syncOrder(String outOrderNo) {
        WechatPayScoreOrder order = requireOrder(outOrderNo);
        MerchantChannel channel = requireEnabledWechatChannel(order.getChannelId());
        TencentWechatPayScoreRequest request = baseRequest(order);
        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .queryPayScoreOrder(buildWechatProperties(channel), request);
        /**
         * 合并渠道响应
         * @param order 支付分订单
         * @param response 渠道响应
         */
        mergeResponse(order, response);
        wechatPayScoreOrderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 完成支付分订单
     *
     * @param outOrderNo 出订单号
     * @param request 请求参数
     * @return 完成后的支付分订单
     */
    public WechatPayScoreOrder completeOrder(String outOrderNo, WechatPayScoreCompleteDTO request) {
        WechatPayScoreOrder order = requireOrder(outOrderNo);
        MerchantChannel channel = requireEnabledWechatChannel(order.getChannelId());
        WechatPayScoreCompleteDTO actualRequest = request == null
                ? new WechatPayScoreCompleteDTO(null, null, null, null, null, null, null, null) : request;
        TencentWechatPayScoreRequest base = baseRequest(order);
        String finishType = firstNonBlank(actualRequest.finishType(), order.getFinishType(), "FINISH");
        String reason = firstNonBlank(actualRequest.reason(), order.getReason(), "服务已完成");
        String endTime = firstNonBlank(actualRequest.endTime(), order.getEndTime());
        Integer totalAmountFen = amountToFen(actualRequest.totalAmount() != null
                ? actualRequest.totalAmount() : order.getTotalAmount());
        TencentWechatPayScoreRequest gatewayRequest = new TencentWechatPayScoreRequest(
                base.appId(),
                base.serviceId(),
                base.outOrderNo(),
                base.openId(),
                base.notifyUrl(),
                base.serviceIntroduction(),
                base.startTime(),
                endTime,
                totalAmountFen,
                reason,
                finishType,
                base.needUserConfirm(),
                base.attach(),
                actualRequest.postPayments(),
                actualRequest.postDiscounts(),
                actualRequest.extraParams());

        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .completePayScoreOrder(buildWechatProperties(channel), gatewayRequest);
        if (actualRequest.totalAmount() != null) {
            order.setTotalAmount(actualRequest.totalAmount());
        }
        order.setFinishType(gatewayRequest.finishType());
        order.setReason(gatewayRequest.reason());
        order.setEndTime(gatewayRequest.endTime());
        if (StringUtils.hasText(actualRequest.remark())) {
            order.setRemark(actualRequest.remark());
        }
        /**
         * 合并渠道响应
         * @param order 支付分订单
         * @param response 渠道响应
         */
        mergeResponse(order, response);
        wechatPayScoreOrderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 取消支付分订单
     *
     * @param outOrderNo 出订单号
     * @param request 请求参数
     * @return 取消后的支付分订单
     */
    public WechatPayScoreOrder cancelOrder(String outOrderNo, WechatPayScoreCancelDTO request) {
        WechatPayScoreOrder order = requireOrder(outOrderNo);
        MerchantChannel channel = requireEnabledWechatChannel(order.getChannelId());
        WechatPayScoreCancelDTO actualRequest = request == null
                ? new WechatPayScoreCancelDTO(null, null, null) : request;
        TencentWechatPayScoreRequest base = baseRequest(order);
        String reason = firstNonBlank(actualRequest.reason(), order.getReason(), "商户取消");
        TencentWechatPayScoreRequest gatewayRequest = new TencentWechatPayScoreRequest(
                base.appId(),
                base.serviceId(),
                base.outOrderNo(),
                base.openId(),
                base.notifyUrl(),
                base.serviceIntroduction(),
                base.startTime(),
                base.endTime(),
                base.totalAmountFen(),
                reason,
                base.finishType(),
                base.needUserConfirm(),
                base.attach(),
                base.postPayments(),
                base.postDiscounts(),
                actualRequest.extraParams());

        TencentWechatPayScoreResponse response = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .cancelPayScoreOrder(buildWechatProperties(channel), gatewayRequest);
        order.setReason(gatewayRequest.reason());
        if (StringUtils.hasText(actualRequest.remark())) {
            order.setRemark(actualRequest.remark());
        }
        /**
         * 合并渠道响应
         * @param order 支付分订单
         * @param response 渠道响应
         */
        mergeResponse(order, response);
        wechatPayScoreOrderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理微信支付分通知
     *
     * @param channelId 渠道标识
     * @param outOrderNo 出订单号
     * @param payload 通知报文
     * @param responsePayload 响应报文
     */
    public void handleNotify(Long channelId, String outOrderNo, TencentWechatPayScoreNotifyPayload payload, String responsePayload) {
        if (payload == null) {
            throw new PaymentException("微信支付分回调内容不能为空");
        }
        if (StringUtils.hasText(outOrderNo) && StringUtils.hasText(payload.outOrderNo()) && !outOrderNo.equals(payload.outOrderNo())) {
            throw new PaymentException("微信支付分回调路径出订单号和报文出订单号不一致");
        }
        WechatPayScoreOrder order = requireOrder(firstNonBlank(payload.outOrderNo(), outOrderNo));
        if (channelId != null && order.getChannelId() != null && !channelId.equals(order.getChannelId())) {
            throw new PaymentException("微信支付分回调渠道与订单渠道不一致");
        }
        order.setServiceOrderNo(firstNonBlank(payload.serviceOrderNo(), order.getServiceOrderNo()));
        order.setAppId(firstNonBlank(payload.appId(), order.getAppId()));
        order.setServiceId(firstNonBlank(payload.serviceId(), order.getServiceId()));
        order.setOpenId(firstNonBlank(payload.openId(), order.getOpenId()));
        order.setState(firstNonBlank(payload.state(), order.getState()));
        order.setFinishReason(firstNonBlank(payload.finishReason(), order.getFinishReason()));
        order.setResponsePayload(firstNonBlank(responsePayload, toJson(payload.rawData()), order.getResponsePayload()));
        /**
         * 更新终态时间戳
         * @param order 支付分订单
         */
        updateTerminalState(order);
        wechatPayScoreOrderMapper.updateById(order);
    }

    /**
     * 获取支付分订单，不存在时抛出异常
     *
     * @param outOrderNo 出订单号
     * @return 获取到的支付分订单
     */
    private WechatPayScoreOrder requireOrder(String outOrderNo) {
        WechatPayScoreOrder order = getByOutOrderNo(outOrderNo);
        if (order == null) {
            throw new PaymentException("微信支付分订单不存在: " + outOrderNo);
        }
        return order;
    }

    /**
     * 获取已启用的微信渠道
     *
     * @param channelId 渠道标识
     * @return 获取到的已启用微信渠道
     */
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
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
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
        properties.setNotifyUrl(firstNonBlank(channel.getNotifyUrl(), extText(channel, "payScoreNotifyUrl")));
        return properties;
    }

    /**
     * 基于已有订单构建网关请求
     *
     * @param order 支付分订单
     * @return 构建出的网关请求
     */
    private TencentWechatPayScoreRequest baseRequest(WechatPayScoreOrder order) {
        TencentWechatPayScoreRequest request = new TencentWechatPayScoreRequest(
                order.getAppId(),
                order.getServiceId(),
                order.getOutOrderNo(),
                order.getOpenId(),
                order.getNotifyUrl(),
                order.getServiceIntroduction(),
                order.getStartTime(),
                order.getEndTime(),
                amountToFen(order.getTotalAmount()),
                order.getReason(),
                order.getFinishType(),
                null,
                order.getAttach(),
                null,
                null,
                null
        );
        return request;
    }

    /**
     * 合并渠道响应到本地订单
     *
     * @param order 支付分订单
     * @param response 渠道响应
     */
    private void mergeResponse(WechatPayScoreOrder order, TencentWechatPayScoreResponse response) {
        order.setServiceOrderNo(firstNonBlank(response.serviceOrderNo(), order.getServiceOrderNo()));
        order.setState(firstNonBlank(response.state(), order.getState()));
        order.setPackageInfo(firstNonBlank(response.packageInfo(), order.getPackageInfo()));
        order.setResponsePayload(firstNonBlank(response.rawResponse(), toJson(response.rawData()), order.getResponsePayload()));
        /**
         * 更新终态时间戳
         * @param order 支付分订单
         */
        updateTerminalState(order);
    }

    /**
     * 按终态补齐完成/取消时间
     *
     * @param order 支付分订单
     */
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

    /**
     * 解析支付分服务 ID：优先取请求值，其次依次读取渠道扩展配置
     *
     * @param channel 渠道
     * @param requestServiceId 请求传入的服务 ID
     * @return 解析出的服务 ID
     */
    private String resolveServiceId(MerchantChannel channel, String requestServiceId) {
        return requiredText(firstNonBlank(
                requestServiceId,
                /**
                 * 读取渠道扩展配置 payScoreServiceId
                 * @param channel 渠道
                 */
                extText(channel, "payScoreServiceId"),
                /**
                 * 读取渠道扩展配置 serviceId
                 * @param channel 渠道
                 */
                extText(channel, "serviceId"),
                /**
                 * 读取渠道扩展配置 wechatPayScoreServiceId
                 * @param channel 渠道
                 */
                extText(channel, "wechatPayScoreServiceId"),
                extText(channel, "payMerchantConfigWechatPaymentPointServiceId")),
                "微信支付分服务ID不能为空");
    }

    /**
     * 金额转分
     *
     * @param amount 金额
     * @return 转换后的分值
     */
    private Integer amountToFen(BigDecimal amount) {
        return amount == null ? null : (int) yuanToFen(amount);
    }

    /**
     * 生成出订单号
     *
     * @return 生成的出订单号
     */
    private String generateOutOrderNo() {
        return "PS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /**
     * 转大写
     *
     * @param value 值
     * @return 转大写后的值
     */
    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
