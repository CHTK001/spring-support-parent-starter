package com.chua.starter.pay.support.refund;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayRefundStatus;
import com.chua.starter.pay.support.pojo.PayMerchantConfigAlipayWrapper;
import com.chua.starter.pay.support.pojo.RefundOrderV2Request;
import com.chua.starter.pay.support.pojo.RefundOrderV2Response;
import com.chua.starter.pay.support.service.PayMerchantConfigAlipayService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 支付宝退款适配器
 *
 * @author CH
 * @since 2025/10/15 17:25
 */
@Slf4j
@Spi({"pay_alipay_app", "pay_alipay_qr_code", "pay_alipay_wap", "pay_alipay_mini"})
public class AlipayRefundOrderAdaptor implements RefundOrderAdaptor {

    @AutoInject
    private PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    private PayMerchantConfigAlipayService payMerchantConfigAlipayService;

    @AutoInject
    private RedissonClient redissonClient;

    @AutoInject
    private TransactionTemplate transactionTemplate;

    @Override
    public ReturnResult<RefundOrderV2Response> refundOrder(PayMerchantOrder merchantOrder, RefundOrderV2Request request) {
        PayMerchantConfigAlipayWrapper configWrapper = payMerchantConfigAlipayService.getByCodeForPayMerchantConfigAlipay(
                merchantOrder.getPayMerchantId(),
                merchantOrder.getPayMerchantTradeType().getName()
        );
        if (!configWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启配置");
        }

        PayMerchantConfigAlipay config = configWrapper.getPayMerchantConfigAlipay();
        RLock lock = redissonClient.getLock(PayConstant.CREATE_REFUND_PREFIX + merchantOrder.getPayMerchantOrderCode());

        lock.lock(3, TimeUnit.SECONDS);
        try {
            return transactionTemplate.execute(it -> {
                RefundOrderV2Response refundOrderV2Response = refundOrderItem(config, merchantOrder, request);
                updateRefundOrder(merchantOrder, refundOrderV2Response, request);
                return ReturnResult.ok(refundOrderV2Response);
            });
        } catch (Exception e) {
            log.error("[支付][退款]支付宝退款失败", e);
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 更新订单
     *
     * @param merchantOrder         支付订单
     * @param refundOrderV2Response 退款结果
     * @param request               退款请求
     */
    private void updateRefundOrder(PayMerchantOrder merchantOrder, RefundOrderV2Response refundOrderV2Response, RefundOrderV2Request request) {
        PayRefundStatus status = refundOrderV2Response.getStatus();
        if (status == PayRefundStatus.SUCCESS) {
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_REFUND_SUCCESS);
            merchantOrder.setPayMerchantOrderRefundSuccessTime(refundOrderV2Response.getSuccessTime());
            merchantOrder.setPayMerchantOrderRefundCreateTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderRefundCode(refundOrderV2Response.getOutRefundNo());
            merchantOrder.setPayMerchantOrderRefundReason(request.getRefundReason());
            payMerchantOrderService.refundOrder(merchantOrder);
            return;
        }

        if (status == PayRefundStatus.PROCESSING) {
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_REFUND_WAITING);
            merchantOrder.setPayMerchantOrderRefundCreateTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderRefundCode(refundOrderV2Response.getOutRefundNo());
            merchantOrder.setPayMerchantOrderRefundReason(request.getRefundReason());
            payMerchantOrderService.updateById(merchantOrder);
            return;
        }

        if (status == PayRefundStatus.CLOSED) {
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CLOSE_SUCCESS);
            merchantOrder.setPayMerchantOrderRefundCreateTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderRefundSuccessTime(refundOrderV2Response.getSuccessTime());
            merchantOrder.setPayMerchantOrderRefundCode(refundOrderV2Response.getOutRefundNo());
            merchantOrder.setPayMerchantOrderRefundReason(request.getRefundReason());
            payMerchantOrderService.updateById(merchantOrder);
        }
    }

    /**
     * 退款
     *
     * @param config           支付宝配置
     * @param payMerchantOrder 支付订单
     * @param refundRequest    退款请求
     * @return 退款结果
     */
    private RefundOrderV2Response refundOrderItem(PayMerchantConfigAlipay config, PayMerchantOrder payMerchantOrder, RefundOrderV2Request refundRequest) {
        try {
            AlipayClient alipayClient = createAlipayClient(config);
            AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
            model.setRefundAmount(refundRequest.getRefundAmount().toString());
            model.setRefundReason(refundRequest.getRefundReason());
            if (payMerchantOrder.getPayMerchantOrderTransactionId() != null) {
                model.setTradeNo(payMerchantOrder.getPayMerchantOrderTransactionId());
            }
            alipayRequest.setBizModel(model);

            AlipayTradeRefundResponse response = alipayClient.execute(alipayRequest);
            RefundOrderV2Response refundOrderV2Response = new RefundOrderV2Response();
            if (response.isSuccess()) {
                refundOrderV2Response.setStatus(PayRefundStatus.SUCCESS);
                refundOrderV2Response.setOutRefundNo(response.getOutTradeNo());
                refundOrderV2Response.setOutTradeNo(response.getOutTradeNo());
                refundOrderV2Response.setTransactionId(response.getTradeNo());
                if (response.getGmtRefundPay() != null) {
                    Date refundPayDate = response.getGmtRefundPay();
                    LocalDateTime refundPayDateTime = refundPayDate.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    refundOrderV2Response.setSuccessTime(refundPayDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            } else {
                refundOrderV2Response.setStatus(PayRefundStatus.CLOSED);
                log.error("[支付][退款]支付宝退款失败: {}", response.getMsg());
            }
            return refundOrderV2Response;
        } catch (AlipayApiException e) {
            log.error("[支付][退款]支付宝退款异常", e);
            throw new RuntimeException("退款失败: " + e.getMessage());
        }
    }

    /**
     * 创建支付宝客户端
     *
     * @param config 支付宝配置
     * @return 支付宝客户端
     */
    private AlipayClient createAlipayClient(PayMerchantConfigAlipay config) {
        String gatewayUrl = config.getPayMerchantConfigAlipayGatewayUrl();
        if (gatewayUrl == null || gatewayUrl.isEmpty()) {
            gatewayUrl = "https://openapi.alipay.com/gateway.do";
        }
        String appId = config.getPayMerchantConfigAlipayAppId();
        String privateKey = config.getPayMerchantConfigAlipayPrivateKey();
        String publicKey = config.getPayMerchantConfigAlipayPublicKey();
        String signType = config.getPayMerchantConfigAlipaySignType();
        if (signType == null || signType.isEmpty()) {
            signType = "RSA2";
        }
        String charset = config.getPayMerchantConfigAlipayCharset();
        if (charset == null || charset.isEmpty()) {
            charset = "UTF-8";
        }
        return new DefaultAlipayClient(gatewayUrl, appId, privateKey, "json", charset, publicKey, signType);
    }
}

