package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.WalletAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 钱包余额支付
 */
@Component
public class WalletBalancePaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    private final WalletAccountService walletAccountService;

    public WalletBalancePaymentChannel(MerchantChannelService merchantChannelService,
                                       ObjectMapper objectMapper,
                                       WalletAccountService walletAccountService) {
        super(merchantChannelService, objectMapper);
        this.walletAccountService = walletAccountService;
    }

    @Override
    public boolean supports(String channelType, String channelSubType) {
        return "WALLET".equalsIgnoreCase(channelType) && "BALANCE".equalsIgnoreCase(channelSubType);
    }

    @Override
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        if (request.getUserId() == null) {
            throw new PaymentException("钱包支付必须提供 userId");
        }
        walletAccountService.pay(channel.getMerchantId(),
                request.getUserId(),
                request.getAmount(),
                request.getOrderNo(),
                "wallet-pay",
                firstNonBlank(request.getSubject(), request.getBody(), request.getOrderNo()));

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
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        if (request.getUserId() == null) {
            throw new PaymentException("钱包退款缺少用户上下文");
        }
        walletAccountService.refund(channel.getMerchantId(),
                request.getUserId(),
                request.getRefundAmount(),
                request.getRefundNo(),
                "wallet-refund",
                request.getReason());

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
