package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.RechargeRequest;
import com.chua.payment.support.channel.TransferRequest;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.dto.WalletOrderQueryRequest;
import com.chua.payment.support.entity.WalletOrder;

/**
 * 钱包订单服务接口，定义充值/转账/提现订单的创建与状态更新契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletOrderService {

    /**
     * 创建充值订单
     */
    WalletOrder createRechargeOrder(RechargeRequest request);

    /**
     * 创建转账订单
     */
    WalletOrder createTransferOrder(TransferRequest request);

    /**
     * 创建提现订单
     */
    WalletOrder createWithdrawOrder(WithdrawRequest request);

    /**
     * 标记订单处理中
     */
    void markProcessing(String orderNo, String thirdPartyOrderNo, String responsePayload);

    /**
     * 标记订单成功
     */
    void markSuccess(String orderNo, String thirdPartyOrderNo, String responsePayload);

    /**
     * 标记订单失败
     */
    void markFailed(String orderNo, String responsePayload, String reason);

    /**
     * 标记订单取消
     */
    void markCancelled(String orderNo, String remark);

    /**
     * 根据订单号查询
     */
    WalletOrder getByOrderNo(String orderNo);

    /**
     * 分页查询
     */
    Page<WalletOrder> page(WalletOrderQueryRequest request);
}

