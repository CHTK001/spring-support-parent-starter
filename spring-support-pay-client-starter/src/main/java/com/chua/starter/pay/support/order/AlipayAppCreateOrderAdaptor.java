package com.chua.starter.pay.support.order;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
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

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * 支付宝APP支付创建订单适配器
 *
 * @author CH
 * @since 2025/10/15 16:00
 */
@Slf4j
@Spi("pay_alipay_app")
public class AlipayAppCreateOrderAdaptor extends WalletCreateOrderAdaptor {

    @AutoInject
    protected PayUserWalletService payUserWalletService;

    @AutoInject
    protected PayMerchantConfigAlipayService payMerchantConfigAlipayService;

    @AutoInject
    protected PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    protected RedissonClient redissonClient;

    @AutoInject
    protected TransactionTemplate transactionTemplate;

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request, String userId, String openId) {
        RLock lock = redissonClient.getLock(PayConstant.CREATE_ORDER_PREFIX + request.getRequestId() + userId);

        lock.lock(3, TimeUnit.SECONDS);
        try {
            PayUserWallet payUserWallet = payUserWalletService.getByUser(userId);
            return transactionTemplate.execute(it -> {
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId, PayTradeType.PAY_ALIPAY_APP, PayOrderStatus.PAY_CREATE);
                return createAlipayOrder(payMerchantOrder);
            });
        } catch (Exception e) {
            log.error("[支付][创建订单]支付宝APP支付创建订单失败", e);
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建支付宝订单
     *
     * @param payMerchantOrder 支付订单
     * @return 创建订单结果
     */
    private ReturnResult<CreateOrderV2Response> createAlipayOrder(PayMerchantOrder payMerchantOrder) {
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
            AlipayTradeAppPayRequest alipayRequest = new AlipayTradeAppPayRequest();
            AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
            model.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
            model.setTotalAmount(payMerchantOrder.getPayMerchantOrderAmount().toString());
            model.setSubject("订单支付");
            model.setProductCode("QUICK_MSECURITY_PAY");
            if (payMerchantOrder.getPayMerchantOrderAttach() != null) {
                model.setBody(payMerchantOrder.getPayMerchantOrderAttach());
            }
            alipayRequest.setBizModel(model);
            alipayRequest.setNotifyUrl(config.getPayMerchantConfigAlipayPayNotifyUrl() + "/" + payMerchantOrder.getPayMerchantOrderCode());

            AlipayTradeAppPayResponse response = alipayClient.sdkExecute(alipayRequest);
            if (response.isSuccess()) {
                CreateOrderV2Response createOrderV2Response = new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode());
                createOrderV2Response.setPrepayId(response.getBody());
                PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
                postprocessor.publish(payMerchantOrder);
                return ReturnResult.ok(createOrderV2Response);
            } else {
                log.error("[支付][创建订单]支付宝APP支付创建订单失败: {}", response.getMsg());
                return ReturnResult.error(response.getMsg());
            }
        } catch (AlipayApiException e) {
            log.error("[支付][创建订单]支付宝APP支付创建订单异常", e);
            return ReturnResult.error("创建订单失败: " + e.getMessage());
        }
    }

    /**
     * 创建支付宝客户端
     *
     * @param config 支付宝配置
     * @return 支付宝客户端
     */
    protected AlipayClient createAlipayClient(PayMerchantConfigAlipay config) {
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

