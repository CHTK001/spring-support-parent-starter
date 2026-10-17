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
     * wallet 账户服务
     */
    private final WalletAccountService walletAccountService;

    /**
     * 创建钱包余额支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param walletAccountService wallet 账户服务
     */
    public WalletBalancePaymentChannel(MerchantChannelService merchantChannelService,
                                       ObjectMapper objectMapper,
                                       WalletAccountService walletAccountService) {
        super(merchantChannelService, objectMapper);
        this.walletAccountService = walletAccountService;
    }

    @Override
    /**
     * 是否支持该通道
     */
    public boolean supports(String channelType, String channelSubType) {
        return "WALLET".equalsIgnoreCase(channelType) && "BALANCE".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 发起支付
     */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        if (request.userId() == null) {
            throw new PaymentException("钱包支付必须提供 userId");
        }
        walletAccountService.pay(new WalletPayRequest(
                channel.getMerchantId(),
                request.userId(),
                request.amount(),
                request.orderNo(),
                "wallet-pay",
                firstNonBlank(request.subject(), request.body(), request.orderNo())));

        PaymentResult result = new PaymentResult(true, "WALLET_PAY_" + request.orderNo(), "钱包余额支付成功", null,
                null, null, null, null, request.amount(), "PAID", "wallet-balance-pay");
        return result;
    }

    @Override
    /**
     * 查询支付结果
     */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        WalletAccountLog log = walletAccountService.getLog("PAY", channel.getMerchantId(), null, orderNo);
        PaymentResult result = new PaymentResult(log != null,
                log != null ? "WALLET_PAY_" + orderNo : null,
                log != null ? "钱包余额支付成功" : "未找到钱包支付记录",
                null, null, null, null, null,
                log != null ? log.getChangeAmount() : null,
                log != null ? "PAID" : "FAILED",
                log != null ? toJson(log) : null);
        return result;
    }

    @Override
    /**
     * 申请退款
     */
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        if (request.userId() == null) {
            throw new PaymentException("钱包退款缺少用户上下文");
        }
        walletAccountService.refund(new WalletRefundRequest(
                channel.getMerchantId(),
                request.userId(),
                request.refundAmount(),
                request.refundNo(),
                "wallet-refund",
                request.reason()));

        RefundResult result = new RefundResult(true, request.refundNo(), "WALLET_REFUND_" + request.refundNo(),
                "钱包退款成功", request.refundAmount(), "REFUNDED", "wallet-balance-refund");
        return result;
    }

    @Override
    /**
     * 查询退款结果
     */
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        WalletAccountLog log = walletAccountService.getLog("REFUND", channel.getMerchantId(), null, request.refundNo());
        RefundResult result = new RefundResult(log != null, request.refundNo(),
                log != null ? "WALLET_REFUND_" + request.refundNo() : null,
                log != null ? "钱包退款成功" : "未找到钱包退款记录",
                log != null ? log.getChangeAmount() : request.refundAmount(),
                log != null ? "REFUNDED" : "FAILED",
                log != null ? toJson(log) : null);
        return result;
    }

}
