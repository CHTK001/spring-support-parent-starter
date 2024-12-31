package com.chua.starter.pay.support.handler.wechat;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayOrderCreator;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 支付订单处理器
 * @author CH
 * @since 2024/12/30
 */
@Spi("wechat_native")
public final class WechatNativePayOrderCreator implements PayOrderCreator {

    final PayMerchantConfigWechat payMerchantConfigWechat;

    public WechatNativePayOrderCreator(PayMerchantConfigWechat payMerchantConfigWechat) {
        this.payMerchantConfigWechat = payMerchantConfigWechat;
    }

    @Override
    public ReturnResult<PayOrderResponse> handle(PayMerchantOrder payMerchantOrder) {
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();

        // 构建service
        NativePayService service = new NativePayService.Builder().config(config).build();
        PrepayRequest request = getPrepayRequest(payMerchantOrder);

        PrepayResponse response = null;
        try {
            response = service.prepay(request);
        } catch (ServiceException e) {
            return  ReturnResult.error(e.getErrorMessage());
        }

        PayOrderResponse payOrderResponse = new PayOrderResponse();
        payOrderResponse.setPrepayId(null);
        payOrderResponse.setUrl(response.getCodeUrl());
        return ReturnResult.ok(payOrderResponse);
    }

    /**
     * 构建请求
     * @param payMerchantOrder 支付订单
     * @return PrepayRequest
     */
    private PrepayRequest getPrepayRequest(PayMerchantOrder payMerchantOrder) {
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(payMerchantOrder.getPayMerchantOrderTotalPrice().multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).intValue());
        request.setAmount(amount);
        request.setAppid(payMerchantOrder.getPayMerchantOrderUserId());
        request.setMchid(payMerchantConfigWechat.getPayMerchantConfigWechatMchId());
        request.setDescription(payMerchantOrder.getPayMerchantOrderProductName());
        request.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatNotifyUrl());
        request.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
        request.setAttach(payMerchantOrder.getPayMerchantOrderRemark());
        return request;
    }
}
