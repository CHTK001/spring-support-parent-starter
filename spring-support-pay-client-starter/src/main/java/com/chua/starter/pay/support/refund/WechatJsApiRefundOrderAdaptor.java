package com.chua.starter.pay.support.refund;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayRefundStatus;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.pojo.RefundOrderV2Request;
import com.chua.starter.pay.support.pojo.RefundOrderV2Response;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 微信退款适配器
 *
 * @author CH
 * @since 2025/10/14 17:25
 */
@Spi({"pay_wechat_js_api", "pay_wechat_native", "pay_WECHAT_H5"})
public class WechatJsApiRefundOrderAdaptor implements RefundOrderAdaptor {
    @AutoInject
    private PayUserWalletService payUserWalletService;

    @AutoInject
    private PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    private PayMerchantConfigWechatService payMerchantConfigWechatService;

    @AutoInject
    private RedissonClient redissonClient;

    @AutoInject
    private TransactionTemplate transactionTemplate;

    @Override
    public ReturnResult<RefundOrderV2Response> refundOrder(PayMerchantOrder merchantOrder, RefundOrderV2Request request) {
        PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat(merchantOrder.getPayMerchantId(), merchantOrder.getPayMerchantTradeType().getName());
        if (!byCodeForPayMerchantConfigWechat.hasConfig()) {
            return ReturnResult.illegal("商户未开启配置");
        }

        PayMerchantConfigWechat payMerchantConfigWechat = byCodeForPayMerchantConfigWechat.getPayMerchantConfigWechat();
        RLock lock = redissonClient.getLock(PayConstant.CREATE_REFUND_PREFIX + merchantOrder.getPayMerchantOrderCode());

        lock.lock(3, TimeUnit.SECONDS);
        try {
            return transactionTemplate.execute(it -> {
                RefundOrderV2Response refundOrderV2Response = refundOrderItem(payMerchantConfigWechat, merchantOrder, request);
                updateRefundOrder(merchantOrder, refundOrderV2Response, request);
                return ReturnResult.ok(refundOrderV2Response);
            });
        } catch (Exception e) {
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
        if(status == PayRefundStatus.SUCCESS) {
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_REFUND_SUCCESS);
            merchantOrder.setPayMerchantOrderRefundSuccessTime(refundOrderV2Response.getSuccessTime());
            merchantOrder.setPayMerchantOrderRefundCreateTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderRefundCode(refundOrderV2Response.getOutRefundNo());
            merchantOrder.setPayMerchantOrderRefundReason(request.getRefundReason());
            merchantOrder.setPayMerchantOrderRefundUserReceivedAccount(merchantOrder.getPayMerchantOrderOpenid());
            payMerchantOrderService.refundOrder(merchantOrder);
            return;
        }

        if(status == PayRefundStatus.PROCESSING) {
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_REFUND_WAITING);
            merchantOrder.setPayMerchantOrderRefundCreateTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderRefundCode(refundOrderV2Response.getOutRefundNo());
            merchantOrder.setPayMerchantOrderRefundReason(request.getRefundReason());
            merchantOrder.setPayMerchantOrderRefundUserReceivedAccount(merchantOrder.getPayMerchantOrderOpenid());
            payMerchantOrderService.updateById(merchantOrder);
            return;
        }

        if(status == PayRefundStatus.CLOSED) {
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CLOSE_SUCCESS);
            merchantOrder.setPayMerchantOrderRefundCreateTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderRefundSuccessTime(refundOrderV2Response.getSuccessTime());
            merchantOrder.setPayMerchantOrderRefundCode(refundOrderV2Response.getOutRefundNo());
            merchantOrder.setPayMerchantOrderRefundReason(request.getRefundReason());
            merchantOrder.setPayMerchantOrderRefundUserReceivedAccount(merchantOrder.getPayMerchantOrderOpenid());
            payMerchantOrderService.updateById(merchantOrder);
            return;
        }

    }

    /**
     * 退款
     *
     * @param payMerchantConfigWechat 微信配置
     * @param payMerchantOrder        支付订单
     * @param refundOrderV2Request    退款请求
     * @return 退款结果
     */
    private RefundOrderV2Response refundOrderItem(PayMerchantConfigWechat payMerchantConfigWechat, PayMerchantOrder payMerchantOrder, RefundOrderV2Request refundOrderV2Request) {
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        // 使用 com.wechat.pay.java.core.util 中的函数从本地文件中加载商户私钥，商户私钥会用来生成请求的签名
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();
        BigDecimal realAmount = payMerchantOrder.getPayMerchantOrderAmount().subtract(refundOrderV2Request.getRefundAmount());
        // 构建service
        RefundService service = new RefundService.Builder().config(config).build();
        CreateRequest request = getPrepayRequest(payMerchantConfigWechat, payMerchantOrder, realAmount, refundOrderV2Request);

        Refund refund = null;
        Status status = null;
        try {
            refund = service.create(request);
            status = refund.getStatus();
        } catch (ServiceException e) {
            throw new RuntimeException(e.getErrorMessage());
        }
        RefundOrderV2Response refundOrderV2Response = new RefundOrderV2Response();
        refundOrderV2Response.setStatus(PayRefundStatus.valueOf(status.name()));
        refundOrderV2Response.setRefundId(refund.getRefundId());
        refundOrderV2Response.setOutRefundNo(refund.getOutRefundNo());
        refundOrderV2Response.setOutTradeNo(refund.getOutTradeNo());
        refundOrderV2Response.setTransactionId(refund.getTransactionId());
        refundOrderV2Response.setSuccessTime(refund.getSuccessTime());
        refundOrderV2Response.setCreateTime(refund.getCreateTime());
        refundOrderV2Response.setUserReceivedAccount(refund.getUserReceivedAccount());
        return refundOrderV2Response;
    }

    /**
     * 构建请求
     *
     * @param payMerchantConfigWechat 微信配置
     * @param payMerchantOrder        支付订单
     * @param refundRequest           退款请求
     * @return PrepayRequest
     */
    private CreateRequest getPrepayRequest(PayMerchantConfigWechat payMerchantConfigWechat, PayMerchantOrder payMerchantOrder, BigDecimal realAmount, RefundOrderV2Request refundRequest) {
        CreateRequest request = new CreateRequest();
        AmountReq amount = new AmountReq();
        amount.setTotal(payMerchantOrder.getPayMerchantOrderAmount().multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).longValue());
        amount.setRefund(realAmount.multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).longValue());
        amount.setCurrency("CNY");

        request.setAmount(amount);
        request.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
        request.setOutRefundNo(payMerchantOrder.getPayMerchantOrderRefundCode());
        request.setReason(refundRequest.getRefundReason());
        request.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatRefundNotifyUrl() + "/" + payMerchantOrder.getPayMerchantOrderCode());
        request.setTransactionId(payMerchantOrder.getPayMerchantOrderTransactionId());
        return request;
    }

}
