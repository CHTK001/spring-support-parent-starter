package com.chua.starter.pay.support.order;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PayMerchantConfigAlipayWrapper;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.service.PayMerchantConfigAlipayService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 支付宝扫码支付创建订单适配器
 *
 * @author CH
 * @since 2025/10/15 16:00
 */
@Slf4j
@Spi("pay_alipay_qr_code")
public class AlipayQrCodeCreateOrderAdaptor extends AlipayAppCreateOrderAdaptor {

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request, String userId, String openId) {
        RLock lock = redissonClient.getLock(PayConstant.CREATE_ORDER_PREFIX + request.getRequestId() + userId);

        lock.lock(3, TimeUnit.SECONDS);
        try {
            PayUserWallet payUserWallet = payUserWalletService.getByUser(userId);
            return transactionTemplate.execute(it -> {
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId, PayTradeType.PAY_ALIPAY_QR_CODE, PayOrderStatus.PAY_CREATE);
                return createAlipayQrCodeOrder(payMerchantOrder);
            });
        } catch (Exception e) {
            log.error("[支付][创建订单]支付宝扫码支付创建订单失败", e);
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建支付宝扫码订单
     *
     * @param payMerchantOrder 支付订单
     * @return 创建订单结果
     */
    private ReturnResult<CreateOrderV2Response> createAlipayQrCodeOrder(PayMerchantOrder payMerchantOrder) {
        PayMerchantConfigAlipayWrapper configWrapper = payMerchantConfigAlipayService.getByCodeForPayMerchantConfigAlipay(
                payMerchantOrder.getPayMerchantId(),
                payMerchantOrder.getPayMerchantTradeType().getName()
        );
        if (!configWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启配置");
        }

        PayMerchantConfigAlipay config = configWrapper.getPayMerchantConfigAlipay();
        try {
            AlipayClient alipayClient = createAlipayClient(config);
            AlipayTradePrecreateRequest alipayRequest = new AlipayTradePrecreateRequest();
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
            model.setTotalAmount(payMerchantOrder.getPayMerchantOrderAmount().toString());
            model.setSubject("订单支付");
            if (payMerchantOrder.getPayMerchantOrderAttach() != null) {
                model.setBody(payMerchantOrder.getPayMerchantOrderAttach());
            }
            alipayRequest.setBizModel(model);
            alipayRequest.setNotifyUrl(config.getPayMerchantConfigAlipayPayNotifyUrl() + "/" + payMerchantOrder.getPayMerchantOrderCode());

            AlipayTradePrecreateResponse response = alipayClient.execute(alipayRequest);
            if (response.isSuccess()) {
                CreateOrderV2Response createOrderV2Response = new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode());
                createOrderV2Response.setUrl(response.getQrCode());
                PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
                postprocessor.publish(payMerchantOrder);
                return ReturnResult.ok(createOrderV2Response);
            } else {
                log.error("[支付][创建订单]支付宝扫码支付创建订单失败: {}", response.getMsg());
                return ReturnResult.error(response.getMsg());
            }
        } catch (AlipayApiException e) {
            log.error("[支付][创建订单]支付宝扫码支付创建订单异常", e);
            return ReturnResult.error("创建订单失败: " + e.getMessage());
        }
    }
}

