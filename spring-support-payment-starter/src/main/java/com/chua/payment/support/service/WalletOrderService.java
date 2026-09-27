package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.RechargeRequest;
import com.chua.payment.support.channel.TransferRequest;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.dto.WalletOrderQueryRequest;
import com.chua.payment.support.entity.WalletOrder;

/**
 * 钱包订单服务接口，定义充值、转账、提现订单的创建与状态更新契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletOrderService {

    /**
     * 创建充值订单。
     * @param request 请求参数
     * @return 创建出的充值订单
     */
    WalletOrder createRechargeOrder(RechargeRequest request);

    /**
     * 创建转账订单。
     * @param request 请求参数
     * @return 创建出的转账订单
     */
    WalletOrder createTransferOrder(TransferRequest request);

    /**
     * 创建提现订单。
     * @param request 请求参数
     * @return 创建出的提现订单
     */
    WalletOrder createWithdrawOrder(WithdrawRequest request);

    /**
     * 标记订单处理中。
     * @param orderNo 订单号
     * @param thirdPartyOrderNo 第三方订单号
     * @param responsePayload 响应报文
     */
    void markProcessing(String orderNo, String thirdPartyOrderNo, String responsePayload);

    /**
     * 标记订单成功。
     * @param orderNo 订单号
     * @param thirdPartyOrderNo 第三方订单号
     * @param responsePayload 响应报文
     */
    void markSuccess(String orderNo, String thirdPartyOrderNo, String responsePayload);

    /**
     * 标记订单失败。
     * @param orderNo 订单号
     * @param responsePayload 响应报文
     * @param reason 失败原因
     */
    void markFailed(String orderNo, String responsePayload, String reason);

    /**
     * 标记订单已取消。
     * @param orderNo 订单号
     * @param remark 备注
     */
    void markCancelled(String orderNo, String remark);

    /**
     * 根据订单号查询。
     * @param orderNo 订单号
     * @return 按订单号查询到的钱包订单
     */
    WalletOrder getByOrderNo(String orderNo);

    /**
     * 分页查询钱包订单。
     * @param request 请求参数
     */
    Page<WalletOrder> page(WalletOrderQueryRequest request);
}
