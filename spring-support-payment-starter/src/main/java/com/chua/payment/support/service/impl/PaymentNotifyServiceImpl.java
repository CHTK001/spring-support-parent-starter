package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.WechatPayNotifyDTO;
import com.chua.payment.support.dto.WechatRefundNotifyDTO;
import com.chua.payment.support.dto.RefundSuccessDTO;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.PaymentNotifyService;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.PaymentRefundOrderService;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 第三方支付异步通知处理，负责微信/支付宝等渠道支付与退款回调解析和状态更新。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Service
public class PaymentNotifyServiceImpl extends AbstractMerchantPaymentChannel implements PaymentNotifyService {

    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;
    /** 薪酬排序映射器 */
    private final PaymentOrderMapper paymentOrderMapper;
    /** Payment排序服务 */
    private final PaymentOrderService paymentOrderService;
    /** Paymentrefund排序服务 */
    private final PaymentRefundOrderService paymentRefundOrderService;
    /** 提供者gateway注册表 */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    /**
    * 创建 薪酬通知服务impl 实例
    * @param merchantChannelService merchant通道服务
    * @param objectMapper 对象映射器
    * @param merchantChannelMapper merchant通道映射器
    * @param paymentOrderMapper 薪酬订单映射器
    * @param paymentOrderService 薪酬订单服务
    * @param paymentRefundOrderService 薪酬退税订单服务
    * @param providerGatewayRegistry 提供者gatewayregistry
    */
    public PaymentNotifyServiceImpl(MerchantChannelService merchantChannelService,
                                    ObjectMapper objectMapper,
                                    MerchantChannelMapper merchantChannelMapper,
                                    PaymentOrderMapper paymentOrderMapper,
                                    PaymentOrderService paymentOrderService,
                                    PaymentRefundOrderService paymentRefundOrderService,
                                    PaymentProviderGatewayRegistry providerGatewayRegistry) {
        /**
         * 父。
         * @param merchantChannelService merchant通道服务
         * @param objectMapper 对象映射器
         */
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentOrderService = paymentOrderService;
        this.paymentRefundOrderService = paymentRefundOrderService;
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理wechat薪酬通知
     * @param channelId 通道标识
     * @param orderNo 订单no
     * @param serialNumber 串行数字
     * @param timestamp 时间戳
     * @param nonce nonce
     * @param signature 签名
     * @param signType 标志类型
     * @param body 主体
     * @param dto dto
     */
    public void handleWechatPayNotify(WechatPayNotifyDTO dto) {
        MerchantChannel channel = requireEnabledChannel(dto.channelId(), "WECHAT");
        TencentWechatPayNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));

        /**
         * 校验通知订单no。
         * @param dto.orderNo( dto.订单no(
         */
        validateNotifyOrderNo(dto.orderNo(), payload.getOrderNo());
        PaymentOrder order = requireOrder(firstNonBlank(dto.orderNo(), payload.getOrderNo()));
        /**
         * 校验订单通道。
         * @param order 订单
         * @param channel 通道
         */
        validateOrderChannel(order, channel);
        /**
         * 校验wechatidentity。
         * @param channel 通道
         * @param payload.getAppId( payload.获取appid(
         */
        validateWechatIdentity(channel, payload.getAppId(), payload.getMerchantId());
        /**
         * 校验amount。
         * @param order.getOrderAmount( 订单.获取订单amount(
         */
        validateAmount(order.getOrderAmount(), fenToYuan(payload.getTotalAmountFen()), "微信支付通知金额不匹配");

        if (!StringUtils.hasText(payload.getTradeState())) {
            /**
             * 薪酬异常。
             * @param "微信支付通知缺少交易状态" "微信支付通知缺少交易状态"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("微信支付通知缺少交易状态");
        }

        switch (payload.getTradeState()) {
            case "SUCCESS", "REFUND" -> paymentOrderService.paySuccess(
                    order.getId(),
                    /**
                     * resolvewechatpaidamount。
                     * @param payload payload
                     */
                    resolveWechatPaidAmount(payload),
                    payload.getTransactionId(),
                    "wechat-notify");
            case "CLOSED", "REVOKED" -> {
                if (canCancel(order.getStatus())) {
                    paymentOrderService.cancelOrder(order.getId(), "wechat-notify", firstNonBlank(payload.getTradeStateDesc(), "微信订单已关闭"));
                } else {
                    log.info("微信支付关闭通知已忽略: orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                }
            }
            case "PAYERROR" -> paymentOrderService.payFail(order.getId(), firstNonBlank(payload.getTradeStateDesc(), "微信支付失败"), "wechat-notify");
            case "NOTPAY", "USERPAYING", "ACCEPT" -> log.info("微信支付通知保持处理中: orderNo={}, tradeState={}", order.getOrderNo(), payload.getTradeState());
            default -> log.info("微信支付通知未触发状态更新: orderNo={}, tradeState={}", order.getOrderNo(), payload.getTradeState());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理wechat退税通知
     * @param channelId 通道标识
     * @param refundNo 退税no
     * @param serialNumber 串行数字
     * @param timestamp 时间戳
     * @param nonce nonce
     * @param signature 签名
     * @param signType 标志类型
     * @param body 主体
     * @param dto dto
     */
    public void handleWechatRefundNotify(WechatRefundNotifyDTO dto) {
        MerchantChannel channel = requireEnabledChannel(dto.channelId(), "WECHAT");
        TencentWechatRefundNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parseRefundNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));

        /**
         * 校验通知退税no。
         * @param dto.refundNo( dto.退税no(
         */
        validateNotifyRefundNo(dto.refundNo(), payload.getRefundNo());
        PaymentRefundOrder refundOrder = resolveRefundOrder(firstNonBlank(dto.refundNo(), payload.getRefundNo()), payload.getRefundId(), payload.getOrderNo());
        PaymentOrder order = requireOrder(refundOrder.getOrderNo());
        /**
         * 校验退税通道。
         * @param refundOrder 退税订单
         * @param channel 通道
         */
        validateRefundChannel(refundOrder, channel);
        /**
         * 校验订单通道。
         * @param order 订单
         * @param channel 通道
         */
        validateOrderChannel(order, channel);
        /**
         * 校验退税订单mapping。
         * @param refundOrder 退税订单
         * @param payload.getOrderNo( payload.获取订单no(
         */
        validateRefundOrderMapping(refundOrder, payload.getOrderNo());
        /**
         * 校验amount。
         * @param order.getOrderAmount( 订单.获取订单amount(
         */
        validateAmount(order.getOrderAmount(), fenToYuan(payload.getTotalAmountFen()), "微信退款通知原订单金额不匹配");

        BigDecimal refundAmount = fenToYuan(payload.getRefundAmountFen());
        if (!StringUtils.hasText(payload.getRefundStatus())) {
            /**
             * 薪酬异常。
             * @param "微信退款通知缺少退款状态" "微信退款通知缺少退款状态"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("微信退款通知缺少退款状态");
        }

        switch (payload.getRefundStatus()) {
            case "SUCCESS" -> paymentOrderService.refundSuccess(new RefundSuccessDTO(
                    refundOrder.getRefundNo(),
                    refundAmount,
                    /**
                     * 第一个nonblank。
                     * @param payload.getRefundId( payload.获取退税标识(
                     */
                    firstNonBlank(payload.getRefundId(), refundOrder.getThirdPartyRefundNo()),
                    "wechat-refund-notify",
                    /**
                     * 转为json。
                     * @param payload payload
                     */
                    toJson(payload),
                    "微信退款回调成功"));
            case "CLOSED", "ABNORMAL" -> paymentOrderService.refundFail(
                    refundOrder.getRefundNo(),
                    /**
                     * 转为json。
                     * @param payload payload
                     */
                    toJson(payload),
                    "wechat-refund-notify",
                    "微信退款失败:" + payload.getRefundStatus());
            case "PROCESSING" -> log.info("微信退款通知保持处理中: orderNo={}, refundNo={}, outRefundNo={}", order.getOrderNo(), refundOrder.getRefundNo(), payload.getRefundNo());
            default -> log.info("微信退款通知未触发状态更新: orderNo={}, refundNo={}, refundStatus={}", order.getOrderNo(), refundOrder.getRefundNo(), payload.getRefundStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理alipay薪酬通知
     *
     * @param channelId 通道标识
     * @param orderNo 订单no
     * @param params 参数
     */
    public void handleAlipayPayNotify(Long channelId, String orderNo, Map<String, String> params) {
        MerchantChannel channel = requireEnabledChannel(channelId, "ALIPAY");
        AliyunAlipayPayNotifyPayload payload = providerGatewayRegistry.aliyunAlipayGateway(providerSpi(channel, null))
                .verifyAndParsePayNotify(buildAlipayProperties(channel), buildAlipayNotifyRequest(params));

        /**
         * 校验通知订单no。
         * @param orderNo 订单no
         * @param payload.getOrderNo( payload.获取订单no(
         */
        validateNotifyOrderNo(orderNo, payload.getOrderNo());
        PaymentOrder order = requireOrder(firstNonBlank(orderNo, payload.getOrderNo()));
        /**
         * 校验订单通道。
         * @param order 订单
         * @param channel 通道
         */
        validateOrderChannel(order, channel);
        /**
         * 校验alipayidentity。
         * @param channel 通道
         * @param payload.getAppId( payload.获取appid(
         */
        validateAlipayIdentity(channel, payload.getAppId(), payload.getSellerId());
        /**
         * 校验amount。
         * @param order.getOrderAmount( 订单.获取订单amount(
         */
        validateAmount(order.getOrderAmount(), payload.getTotalAmount(), "支付宝支付通知金额不匹配");

        String tradeStatus = payload.getTradeStatus();
        if (!StringUtils.hasText(tradeStatus)) {
            /**
             * 薪酬异常。
             * @param trade_status" 贸易状态"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付宝支付通知缺少 trade_status");
        }

        switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> paymentOrderService.paySuccess(order.getId(), payload.getTotalAmount(), payload.getTradeNo(), "alipay-notify");
            case "TRADE_CLOSED" -> {
                if (canCancel(order.getStatus())) {
                    paymentOrderService.cancelOrder(order.getId(), "alipay-notify", "支付宝交易已关闭");
                } else {
                    log.info("支付宝关闭通知已忽略: orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                }
            }
            case "WAIT_BUYER_PAY" -> log.info("支付宝支付通知保持处理中: orderNo={}", order.getOrderNo());
            default -> log.info("支付宝通知未触发状态更新: orderNo={}, tradeStatus={}", order.getOrderNo(), tradeStatus);
        }
    }

    /**
     * require已启用通道
     *
     * @param channelId 通道标识
     * @param channelType 通道类型
     * @return require已启用通道的结果
     */
    private MerchantChannel requireEnabledChannel(Long channelId, String channelType) {
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            /**
             * 薪酬异常。
             * @param "支付方式不存在" "支付方式不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式不存在");
        }
        if (!channelType.equalsIgnoreCase(channel.getChannelType())) {
            /**
             * 薪酬异常。
             * @param "支付方式类型不匹配" "支付方式类型不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式类型不匹配");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
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
     * require订单
     *
     * @param orderNo 订单no
     * @return require订单的结果
     */
    private PaymentOrder requireOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            /**
             * 薪酬异常。
             * @param "通知中缺少订单号" "通知中缺少订单号"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("通知中缺少订单号");
        }
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .last("limit 1"));
        if (order == null) {
            /**
             * 薪酬异常。
             * @param orderNo 订单no
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单不存在: " + orderNo);
        }
        return order;
    }

    /**
     * 校验订单通道
     *
     * @param order 订单
     * @param channel 通道
     */
    private void validateOrderChannel(PaymentOrder order, MerchantChannel channel) {
        if (order.getChannelId() == null || !order.getChannelId().equals(channel.getId())) {
            /**
             * 薪酬异常。
             * @param "订单和回调支付方式不匹配" "订单和回调支付方式不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单和回调支付方式不匹配");
        }
    }

    /**
     * 校验退税通道
     *
     * @param refundOrder 退税订单
     * @param channel 通道
     */
    private void validateRefundChannel(PaymentRefundOrder refundOrder, MerchantChannel channel) {
        if (refundOrder.getChannelId() == null || !refundOrder.getChannelId().equals(channel.getId())) {
            /**
             * 薪酬异常。
             * @param "退款单和回调支付方式不匹配" "退款单和回调支付方式不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款单和回调支付方式不匹配");
        }
    }

    /**
     * 校验通知订单no
     *
     * @param pathOrderNo 路径订单no
     * @param payloadOrderNo payload订单no
     */
    private void validateNotifyOrderNo(String pathOrderNo, String payloadOrderNo) {
        if (StringUtils.hasText(pathOrderNo) && StringUtils.hasText(payloadOrderNo) && !pathOrderNo.equals(payloadOrderNo)) {
            /**
             * 薪酬异常。
             * @param "回调路径订单号和报文订单号不一致" "回调路径订单号和报文订单号不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("回调路径订单号和报文订单号不一致");
        }
    }

    /**
     * 校验通知退税no
     *
     * @param pathRefundNo 路径退税no
     * @param payloadRefundNo payload退税no
     */
    private void validateNotifyRefundNo(String pathRefundNo, String payloadRefundNo) {
        if (StringUtils.hasText(pathRefundNo) && StringUtils.hasText(payloadRefundNo) && !pathRefundNo.equals(payloadRefundNo)) {
            /**
             * 薪酬异常。
             * @param "回调路径退款单号和报文退款单号不一致" "回调路径退款单号和报文退款单号不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("回调路径退款单号和报文退款单号不一致");
        }
    }

    /**
     * 校验退税订单mapping
     *
     * @param refundOrder 退税订单
     * @param payloadOrderNo payload订单no
     */
    private void validateRefundOrderMapping(PaymentRefundOrder refundOrder, String payloadOrderNo) {
        if (refundOrder == null || !StringUtils.hasText(payloadOrderNo)) {
            return;
        }
        if (!payloadOrderNo.equals(refundOrder.getOrderNo())) {
            /**
             * 薪酬异常。
             * @param "退款回调订单号和本地退款单不一致" "退款回调订单号和本地退款单不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款回调订单号和本地退款单不一致");
        }
    }

    /**
     * 解析退税订单
     *
     * @param refundNo 退税no
     * @param thirdPartyRefundNo thirdparty退税no
     * @param orderNo 订单no
     * @return resolve退税订单的结果
     */
    private PaymentRefundOrder resolveRefundOrder(String refundNo, String thirdPartyRefundNo, String orderNo) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getByRefundNo(refundNo);
        if (refundOrder == null) {
            refundOrder = paymentRefundOrderService.getByThirdPartyRefundNo(thirdPartyRefundNo);
        }
        if (refundOrder == null && StringUtils.hasText(orderNo)) {
            PaymentOrder order = requireOrder(orderNo);
            refundOrder = firstNonNull(
                    paymentRefundOrderService.getProcessingByOrderId(order.getId()),
                    paymentRefundOrderService.getLatestByOrderId(order.getId()));
        }
        if (refundOrder == null) {
            /**
             * 薪酬异常。
             * @param orderNo 订单no
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款单不存在: " + firstNonBlank(refundNo, thirdPartyRefundNo, orderNo));
        }
        return refundOrder;
    }

    /**
     * 校验wechatidentity
     *
     * @param channel 通道
     * @param appId appid
     * @param merchantNo merchantno
     */
    private void validateWechatIdentity(MerchantChannel channel, String appId, String merchantNo) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            /**
             * 薪酬异常。
             * @param 不匹配" 不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("微信支付通知 AppId 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(merchantNo) && !channel.getMerchantNo().equals(merchantNo)) {
            /**
             * 薪酬异常。
             * @param "微信支付通知商户号不匹配" "微信支付通知商户号不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("微信支付通知商户号不匹配");
        }
    }

    /**
     * 校验alipayidentity
     *
     * @param channel 通道
     * @param appId appid
     * @param sellerId sellerid
     */
    private void validateAlipayIdentity(MerchantChannel channel, String appId, String sellerId) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            /**
             * 薪酬异常。
             * @param 不匹配" 不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付宝通知 app_id 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(sellerId) && !channel.getMerchantNo().equals(sellerId)) {
            /**
             * 薪酬异常。
             * @param 不匹配" 不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付宝通知 seller_id 不匹配");
        }
    }

    /**
     * 校验Amount
     *
     * @param expected 期望
     * @param actual actual
     * @param message 消息
     */
    private void validateAmount(BigDecimal expected, BigDecimal actual, String message) {
        if (expected == null || actual == null) {
            return;
        }
        if (expected.compareTo(actual) != 0) {
            /**
             * 薪酬异常。
             * @param message 消息
             * @return 薪酬异常的结果
             */
            throw new PaymentException(message);
        }
    }

    /**
     * 解析wechatpaidamount
     *
     * @param payload payload
     * @return resolveWechatPaidAmount的结果
     */
    private BigDecimal resolveWechatPaidAmount(TencentWechatPayNotifyPayload payload) {
        if (payload == null) {
            return null;
        }
        Long paidAmountFen = payload.getPayerTotalAmountFen() != null ? payload.getPayerTotalAmountFen() : payload.getTotalAmountFen();
        return paidAmountFen == null ? null : fenToYuan(paidAmountFen);
    }

    /**
     * 是否可以Cancel
     *
     * @param status 状态
     * @return 能否cancel的结果
     */
    private boolean canCancel(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    @SafeVarargs
    /**
     * 第一个non空
     *
     * @param values 值
     * @return 第一个non空的结果
     */
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
     * 构建Alipay通知请求
     *
     * @param params 参数
     * @return 构建alipay通知请求的结果
     */
    private AliyunAlipayNotifyRequest buildAlipayNotifyRequest(Map<String, String> params) {
        AliyunAlipayNotifyRequest request = new AliyunAlipayNotifyRequest();
        request.setParams(params);
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

    /**
     * 构建alipay属性
     *
     * @param channel 通道
     * @return 构建alipay属性的结果
     */
    private AliyunAlipayProperties buildAlipayProperties(MerchantChannel channel) {
        AliyunAlipayProperties properties = new AliyunAlipayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "支付宝应用ID不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "支付宝私钥不能为空"));
        properties.setAlipayPublicKey(requiredText(channel.getPublicKey(), "支付宝公钥不能为空"));
        properties.setSandbox(channel.getSandboxMode() != null && channel.getSandboxMode() == 1);
        properties.setServerUrl(extText(channel, "serverUrl"));
        properties.setCharset(firstNonBlank(extText(channel, "charset"), "UTF-8"));
        properties.setFormat(firstNonBlank(extText(channel, "format"), "json"));
        properties.setSignType(firstNonBlank(extText(channel, "signType"), "RSA2"));
        return properties;
    }
}

