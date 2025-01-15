package com.chua.starter.pay.support.handler.wechat;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayOrderRefundCreator;
import com.chua.starter.pay.support.pojo.PayRefundRequest;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PayRefundStatus;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 支付退款处理器
 * @author CH
 * @since 2024/12/30
 */
@Spi({"wechat_native", "wechat_js_api", "wechat_h5"})
public final class WechatPayOrderRefundCreator implements PayOrderRefundCreator {

    final PayMerchantConfigWechat payMerchantConfigWechat;

    public WechatPayOrderRefundCreator(PayMerchantConfigWechat payMerchantConfigWechat) {
        this.payMerchantConfigWechat = payMerchantConfigWechat;
    }

    @Override
    public ReturnResult<PayRefundResponse> handle(PayMerchantOrder payMerchantOrder, PayRefundRequest refundRequest) {
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        // 使用 com.wechat.pay.java.core.util 中的函数从本地文件中加载商户私钥，商户私钥会用来生成请求的签名
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();

        // 构建service
        RefundService service = new RefundService.Builder().config(config).build();
        CreateRequest request = getPrepayRequest(payMerchantOrder, refundRequest);

        Refund refund = null;
        Status status = null;
        try {
            refund = service.create(request);
            status = refund.getStatus();
        } catch (ServiceException e) {
            throw new RuntimeException(e.getErrorMessage());
        }
        PayRefundResponse payRefundResponse = new PayRefundResponse();
        payRefundResponse.setStatus(PayRefundStatus.valueOf(status.name()));
        payRefundResponse.setRefundId(refund.getRefundId());
        payRefundResponse.setOutRefundNo(refund.getOutRefundNo());
        payRefundResponse.setOutTradeNo(refund.getOutTradeNo());
        payRefundResponse.setTransactionId(refund.getTransactionId());
        payRefundResponse.setSuccessTime(refund.getSuccessTime());
        payRefundResponse.setCreateTime(refund.getCreateTime());
        payRefundResponse.setUserReceivedAccount(refund.getUserReceivedAccount());
        return ReturnResult.ok(payRefundResponse);
    }

    /**
     * 构建请求
     *
     * @param payMerchantOrder 支付订单
     * @param refundRequest
     * @return PrepayRequest
     */
    private CreateRequest getPrepayRequest(PayMerchantOrder payMerchantOrder, PayRefundRequest refundRequest) {
        CreateRequest request = new CreateRequest();
        AmountReq amount = new AmountReq();
        amount.setTotal(getTotalMoney(payMerchantOrder, refundRequest).multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).longValue());
        amount.setRefund(getTotalMoney(payMerchantOrder, refundRequest).multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).longValue());
        amount.setCurrency("CNY");

        request.setAmount(amount);
        request.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
        request.setOutRefundNo(payMerchantOrder.getPayMerchantOrderRefundCode());
        request.setReason(refundRequest.getRefundReason());
        request.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatRefundNotifyUrl());
        request.setTransactionId(payMerchantOrder.getPayMerchantOrderTransactionId());
        return request;
    }

    private BigDecimal getMoney(PayMerchantOrder payMerchantOrder) {
        return payMerchantOrder.getPayMerchantOrderPrice();
    }
    private BigDecimal getTotalMoney(PayMerchantOrder payMerchantOrder, PayRefundRequest refundRequest) {
        if(null == refundRequest.getMoney()) {
            return payMerchantOrder.getPayMerchantOrderTotalPrice();
        }

        return refundRequest.getMoney();
    }
}
