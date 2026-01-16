package com.chua.starter.pay.support.order;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.Amount;
import com.wechat.pay.java.service.payments.h5.model.PrepayRequest;
import com.wechat.pay.java.service.payments.h5.model.PrepayResponse;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

/**
 * 微信扫码支付
 *
 * @author CH
 * @since 2025/10/14 13:51
 */
@Spi("pay_WECHAT_H5")
public class WechatH5CreateOrderAdaptor extends WalletCreateOrderAdaptor {

    @AutoInject
    private PayUserWalletService payUserWalletService;

    @AutoInject
    private PayMerchantConfigWechatService payMerchantConfigWechatService;

    @AutoInject
    private PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    private RedissonClient redissonClient;

    @AutoInject
    private TransactionTemplate transactionTemplate;

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request, String userId, String openId) {
        RLock lock = redissonClient.getLock(PayConstant.CREATE_ORDER_PREFIX + request.getRequestId() + userId);

        lock.lock(3, TimeUnit.SECONDS);
        try {
            PayUserWallet payUserWallet = payUserWalletService.getByUser(userId);
            return transactionTemplate.execute(it -> {
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId, PayTradeType.PAY_WECHAT_JS_API, PayOrderStatus.PAY_CREATE);
                return createWechatOrder(payMerchantOrder);
            });
        } catch (Exception e) {
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    private ReturnResult<CreateOrderV2Response> createWechatOrder(PayMerchantOrder payMerchantOrder) {
        PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat(payMerchantOrder.getPayMerchantId(), payMerchantOrder.getPayMerchantTradeType().getName());
        if (!byCodeForPayMerchantConfigWechat.hasConfig()) {
            return ReturnResult.illegal("商户未开启配置");
        }
        PayMerchantConfigWechat payMerchantConfigWechat = byCodeForPayMerchantConfigWechat.getPayMerchantConfigWechat();
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();
        // 构建service
        H5Service service = new H5Service.Builder().config(config).build();
        PrepayRequest request = getPrepayRequest(payMerchantOrder, payMerchantConfigWechat);

        PrepayResponse response = null;
        try {
            response = service.prepay(request);
        } catch (ServiceException e) {
            return ReturnResult.error(e.getErrorMessage());
        }

        CreateOrderV2Response payOrderResponse = new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode());
        payOrderResponse.setUrl(response.getH5Url());
        PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
        postprocessor.publish(payMerchantOrder);
        return ReturnResult.ok(payOrderResponse);
    }

    /**
     * 构建请求
     *
     * @param payMerchantOrder        支付订单
     * @param payMerchantConfigWechat 微信配置
     * @return PrepayRequest
     */
    private PrepayRequest getPrepayRequest(PayMerchantOrder payMerchantOrder, PayMerchantConfigWechat payMerchantConfigWechat) {
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(payMerchantOrder.getPayMerchantOrderAmount().multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).intValue());
        request.setAmount(amount);
        request.setAppid(payMerchantConfigWechat.getPayMerchantConfigWechatAppId());
        request.setMchid(payMerchantConfigWechat.getPayMerchantConfigWechatMchId());
        request.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatPayNotifyUrl()+ "/" +payMerchantOrder.getPayMerchantOrderCode());
        request.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
        request.setAttach(payMerchantOrder.getPayMerchantOrderAttach());

        return request;
    }

}
