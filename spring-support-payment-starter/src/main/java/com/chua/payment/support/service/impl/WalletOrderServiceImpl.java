package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.RechargeRequest;
import com.chua.payment.support.channel.TransferRequest;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.dto.WalletOrderQueryRequest;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.WalletOrderMapper;
import com.chua.payment.support.service.PaymentCallbackUrlResolver;
import com.chua.payment.support.service.WalletOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 钱包订单服务实现，负责钱包充值、转账、提现等资金流水订单的管理。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class WalletOrderServiceImpl implements WalletOrderService {

    /**
     * 钱包订单映射器
     */
    private final WalletOrderMapper walletOrderMapper;
    /**
     * 支付回调地址解析器
     */
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;
    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建充值订单
     *
     * @param request 请求参数
     * @return 创建出的充值订单
     */
    public WalletOrder createRechargeOrder(RechargeRequest request) {
        String orderNo = StringUtils.hasText(request.getRechargeNo()) ? request.getRechargeNo() : generateOrderNo("RCH");
        request.setRechargeNo(orderNo);
        if (!StringUtils.hasText(request.getNotifyUrl())) {
            request.setNotifyUrl(paymentCallbackUrlResolver.defaultWalletNotifyUrl("recharge", orderNo));
        }

        WalletOrder existing = getByOrderNo(orderNo);
        if (existing != null) {
            return existing;
        }

        WalletOrder order = new WalletOrder();
        order.setOrderNo(orderNo);
        order.setOrderType("RECHARGE");
        order.setMerchantId(request.getMerchantId());
        order.setUserId(request.getUserId());
        order.setAmount(request.getAmount());
        order.setStatus("PENDING");
        order.setOperator(request.getOperator());
        order.setRemark(request.getRemark());
        order.setRequestPayload(safeJson(request));
        walletOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建转账订单
     *
     * @param request 请求参数
     * @return 创建出的转账订单
     */
    public WalletOrder createTransferOrder(TransferRequest request) {
        String orderNo = StringUtils.hasText(request.getTransferNo()) ? request.getTransferNo() : generateOrderNo("TRF");
        request.setTransferNo(orderNo);
        if (!StringUtils.hasText(request.getNotifyUrl())) {
            request.setNotifyUrl(paymentCallbackUrlResolver.defaultWalletNotifyUrl("transfer", orderNo));
        }

        WalletOrder existing = getByOrderNo(orderNo);
        if (existing != null) {
            return existing;
        }

        WalletOrder order = new WalletOrder();
        order.setOrderNo(orderNo);
        order.setOrderType("TRANSFER");
        order.setMerchantId(request.getMerchantId());
        order.setUserId(request.getFromUserId());
        order.setRelatedUserId(request.getToUserId());
        order.setAmount(request.getAmount());
        order.setStatus("PENDING");
        order.setOperator(request.getOperator());
        order.setRemark(request.getRemark());
        order.setRequestPayload(safeJson(request));
        walletOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建提现订单
     *
     * @param request 请求参数
     * @return 创建出的提现订单
     */
    public WalletOrder createWithdrawOrder(WithdrawRequest request) {
        String orderNo = StringUtils.hasText(request.getWithdrawNo()) ? request.getWithdrawNo() : generateOrderNo("WDW");
        request.setWithdrawNo(orderNo);
        if (!StringUtils.hasText(request.getNotifyUrl())) {
            request.setNotifyUrl(paymentCallbackUrlResolver.defaultWalletNotifyUrl("withdraw", orderNo));
        }

        WalletOrder existing = getByOrderNo(orderNo);
        if (existing != null) {
            return existing;
        }

        WalletOrder order = new WalletOrder();
        order.setOrderNo(orderNo);
        order.setOrderType("WITHDRAW");
        order.setMerchantId(request.getMerchantId());
        order.setUserId(request.getUserId());
        order.setAmount(request.getAmount());
        order.setBankAccount(request.getBankAccount());
        order.setBankName(request.getBankName());
        order.setAccountName(request.getAccountName());
        order.setStatus("PENDING");
        order.setOperator(request.getOperator());
        order.setRemark(request.getRemark());
        order.setRequestPayload(safeJson(request));
        walletOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记订单处理中
     *
     * @param orderNo 订单号
     * @param thirdPartyOrderNo 第三方订单号
     * @param responsePayload 响应报文
     */
    public void markProcessing(String orderNo, String thirdPartyOrderNo, String responsePayload) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("PROCESSING");
        order.setThirdPartyOrderNo(thirdPartyOrderNo);
        order.setResponsePayload(responsePayload);
        walletOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记订单成功
     *
     * @param orderNo 订单号
     * @param thirdPartyOrderNo 第三方订单号
     * @param responsePayload 响应报文
     */
    public void markSuccess(String orderNo, String thirdPartyOrderNo, String responsePayload) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("SUCCESS");
        if (StringUtils.hasText(thirdPartyOrderNo)) {
            order.setThirdPartyOrderNo(thirdPartyOrderNo);
        }
        order.setResponsePayload(responsePayload);
        order.setCompletedAt(LocalDateTime.now());
        walletOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记订单失败
     *
     * @param orderNo 订单号
     * @param responsePayload 响应报文
     * @param reason 失败原因
     */
    public void markFailed(String orderNo, String responsePayload, String reason) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("FAILED");
        order.setResponsePayload(responsePayload);
        order.setRemark(reason);
        walletOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记订单已取消
     *
     * @param orderNo 订单号
     * @param remark 备注
     */
    public void markCancelled(String orderNo, String remark) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("CANCELLED");
        order.setRemark(remark);
        walletOrderMapper.updateById(order);
    }

    @Override
    /**
     * 根据订单号查询钱包订单
     */
    public WalletOrder getByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return walletOrderMapper.selectOne(new LambdaQueryWrapper<WalletOrder>()
                .eq(WalletOrder::getOrderNo, orderNo)
                .last("limit 1"));
    }

    @Override
    public Page<WalletOrder> page(WalletOrderQueryRequest request) {
        if (request == null) {
            throw new PaymentException("查询参数不能为空");
        }
        LambdaQueryWrapper<WalletOrder> wrapper = new LambdaQueryWrapper<WalletOrder>()
                .orderByDesc(WalletOrder::getCreateTime);
        if (request.merchantId() != null) {
            wrapper.eq(WalletOrder::getMerchantId, request.merchantId());
        }
        if (request.userId() != null) {
            wrapper.eq(WalletOrder::getUserId, request.userId());
        }
        if (StringUtils.hasText(request.orderType())) {
            wrapper.eq(WalletOrder::getOrderType, request.orderType());
        }
        if (StringUtils.hasText(request.status())) {
            wrapper.eq(WalletOrder::getStatus, request.status());
        }
        return walletOrderMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper);
    }

    /**
     * 获取钱包订单，不存在时抛出异常
     *
     * @param orderNo 订单号
     * @return 获取到的钱包订单
     */
    private WalletOrder requireOrder(String orderNo) {
        WalletOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new PaymentException("钱包订单不存在: " + orderNo);
        }
        return order;
    }

    /**
     * 生成钱包订单号
     *
     * @param prefix 前缀
     * @return 生成的钱包订单号
     */
    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 安全序列化
     *
     * @param value 值
     */
    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
