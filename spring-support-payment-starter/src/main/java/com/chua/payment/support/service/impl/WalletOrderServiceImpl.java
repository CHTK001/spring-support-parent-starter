package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.RechargeRequest;
import com.chua.payment.support.channel.TransferRequest;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.WalletOrderMapper;
import com.chua.payment.support.service.WalletOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 钱包订单服务实现
 */
@Service
@RequiredArgsConstructor
public class WalletOrderServiceImpl implements WalletOrderService {

    private final WalletOrderMapper walletOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletOrder createRechargeOrder(RechargeRequest request) {
        String orderNo = StringUtils.hasText(request.getRechargeNo()) ? request.getRechargeNo() : generateOrderNo("RCH");

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
        walletOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletOrder createTransferOrder(TransferRequest request) {
        String orderNo = StringUtils.hasText(request.getTransferNo()) ? request.getTransferNo() : generateOrderNo("TRF");

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
        walletOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletOrder createWithdrawOrder(WithdrawRequest request) {
        String orderNo = StringUtils.hasText(request.getWithdrawNo()) ? request.getWithdrawNo() : generateOrderNo("WDW");

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
        walletOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markProcessing(String orderNo, String thirdPartyOrderNo, String responsePayload) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("PROCESSING");
        order.setThirdPartyOrderNo(thirdPartyOrderNo);
        order.setResponsePayload(responsePayload);
        walletOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(String orderNo, String responsePayload) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("SUCCESS");
        order.setResponsePayload(responsePayload);
        order.setCompletedAt(LocalDateTime.now());
        walletOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String orderNo, String responsePayload, String reason) {
        WalletOrder order = requireOrder(orderNo);
        order.setStatus("FAILED");
        order.setResponsePayload(responsePayload);
        order.setRemark(reason);
        walletOrderMapper.updateById(order);
    }

    @Override
    public WalletOrder getByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return walletOrderMapper.selectOne(new LambdaQueryWrapper<WalletOrder>()
                .eq(WalletOrder::getOrderNo, orderNo)
                .last("limit 1"));
    }

    @Override
    public Page<WalletOrder> page(int pageNum, int pageSize, Long merchantId, Long userId, String orderType, String status) {
        LambdaQueryWrapper<WalletOrder> wrapper = new LambdaQueryWrapper<WalletOrder>()
                .orderByDesc(WalletOrder::getCreateTime);
        if (merchantId != null) {
            wrapper.eq(WalletOrder::getMerchantId, merchantId);
        }
        if (userId != null) {
            wrapper.eq(WalletOrder::getUserId, userId);
        }
        if (StringUtils.hasText(orderType)) {
            wrapper.eq(WalletOrder::getOrderType, orderType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(WalletOrder::getStatus, status);
        }
        return walletOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private WalletOrder requireOrder(String orderNo) {
        WalletOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new PaymentException("钱包订单不存在: " + orderNo);
        }
        return order;
    }

    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
