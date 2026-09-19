package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.dto.WalletPayRequest;
import com.chua.payment.support.dto.WalletRefundRequest;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.WalletAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 钱包余额支付
 * @author CH
 * @since 4.0.0
 */
public class WalletBalancePaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    /**
     * Walletaccount服务
    */
    private final WalletAccountService walletAccountService;

    /**
     * 创建 walletbalance薪酬通道 实例
     * @param merchantChannelService merchant通道服务
     * @param objectMapper 对象映射器
     * @param walletAccountService wallet账户服务
     */
    public WalletBalancePaymentChannel(MerchantChannelService merchantChannelService,
                                       ObjectMapper objectMapper,
                                       WalletAccountService walletAccountService) {
        /**
         * 父。
         * @param merchantChannelService merchant通道服务
         * @param objectMapper 对象映射器
         */
        super(merchantChannelService, objectMapper);
        this.walletAccountService = walletAccountService;
    }

    @Override
    /**
     * 支持
    */
    public boolean supports(String channelType, String channelSubType) {
        return "WALLET".equalsIgnoreCase(channelType) && "BALANCE".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 薪酬
    */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        if (request.getUserId() == null) {
            /**
             * 薪酬异常。
             * @param userId" 用户标识"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("钱包支付必须提供 userId");
        }
        walletAccountService.pay(new WalletPayRequest(
                channel.getMerchantId(),
                request.getUserId(),
                request.getAmount(),
                request.getOrderNo(),
                "wallet-pay",
                firstNonBlank(request.getSubject(), request.getBody(), request.getOrderNo())));

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setTradeNo("WALLET_PAY_" + request.getOrderNo());
        result.setPaidAmount(request.getAmount());
        result.setStatus("PAID");
        result.setMessage("钱包余额支付成功");
        result.setRawResponse("wallet-balance-pay");
        return result;
    }

    @Override
    /**
     * 查询
    */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        WalletAccountLog log = walletAccountService.getLog("PAY", channel.getMerchantId(), null, orderNo);
        PaymentResult result = new PaymentResult();
        result.setSuccess(log != null);
        result.setTradeNo(log != null ? "WALLET_PAY_" + orderNo : null);
        result.setPaidAmount(log != null ? log.getChangeAmount() : null);
        result.setStatus(log != null ? "PAID" : "FAILED");
        result.setMessage(log != null ? "钱包余额支付成功" : "未找到钱包支付记录");
        result.setRawResponse(log != null ? toJson(log) : null);
        return result;
    }

    @Override
    /**
     * 退税
    */
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        if (request.getUserId() == null) {
            /**
             * 薪酬异常。
             * @param "钱包退款缺少用户上下文" "钱包退款缺少用户上下文"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("钱包退款缺少用户上下文");
        }
        walletAccountService.refund(new WalletRefundRequest(
                channel.getMerchantId(),
                request.getUserId(),
                request.getRefundAmount(),
                request.getRefundNo(),
                "wallet-refund",
                request.getReason()));

        RefundResult result = new RefundResult();
        result.setSuccess(true);
        result.setRefundNo(request.getRefundNo());
        result.setTradeNo("WALLET_REFUND_" + request.getRefundNo());
        result.setRefundAmount(request.getRefundAmount());
        result.setStatus("REFUNDED");
        result.setMessage("钱包退款成功");
        result.setRawResponse("wallet-balance-refund");
        return result;
    }

    @Override
    /**
     * 查询退税
    */
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        WalletAccountLog log = walletAccountService.getLog("REFUND", channel.getMerchantId(), null, request.getRefundNo());
        RefundResult result = new RefundResult();
        result.setSuccess(log != null);
        result.setRefundNo(request.getRefundNo());
        result.setTradeNo(log != null ? "WALLET_REFUND_" + request.getRefundNo() : null);
        result.setRefundAmount(log != null ? log.getChangeAmount() : request.getRefundAmount());
        result.setStatus(log != null ? "REFUNDED" : "FAILED");
        result.setMessage(log != null ? "钱包退款成功" : "未找到钱包退款记录");
        result.setRawResponse(log != null ? toJson(log) : null);
        return result;
    }

}

