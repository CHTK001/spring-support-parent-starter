package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.service.WalletNotifyService;
import com.chua.payment.support.service.WalletOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 钱包异步回调处理服务实现
 */
@Service
@RequiredArgsConstructor
public class WalletNotifyServiceImpl implements WalletNotifyService {

    private final WalletOrderService walletOrderService;
    private final WalletAccountService walletAccountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(String orderType,
                             String orderNo,
                             String thirdPartyOrderNo,
                             String status,
                             String payload,
                             String reason) {
        if (!StringUtils.hasText(orderNo)) {
            throw new PaymentException("钱包回调缺少订单号");
        }
        WalletOrder walletOrder = walletOrderService.getByOrderNo(orderNo);
        if (walletOrder == null) {
            throw new PaymentException("钱包订单不存在: " + orderNo);
        }
        validateOrderType(orderType, walletOrder);

        String normalizedStatus = normalizeStatus(status);
        switch (normalizedStatus) {
            case "SUCCESS" -> handleSuccess(walletOrder, thirdPartyOrderNo, payload, reason);
            case "PROCESSING" -> walletOrderService.markProcessing(orderNo, thirdPartyOrderNo, payload);
            case "FAILED" -> walletOrderService.markFailed(orderNo, payload, StringUtils.hasText(reason) ? reason : "钱包回调失败");
            default -> throw new PaymentException("不支持的钱包回调状态: " + status);
        }
    }

    private void handleSuccess(WalletOrder walletOrder,
                               String thirdPartyOrderNo,
                               String payload,
                               String reason) {
        if (!"SUCCESS".equalsIgnoreCase(walletOrder.getStatus())) {
            applyAccountChange(walletOrder, reason);
        }
        walletOrderService.markSuccess(walletOrder.getOrderNo(), thirdPartyOrderNo, payload);
    }

    private void applyAccountChange(WalletOrder walletOrder, String reason) {
        String operator = firstNonBlank(walletOrder.getOperator(), "wallet-notify");
        String remark = firstNonBlank(reason, walletOrder.getRemark(), walletOrder.getOrderNo());
        String normalizedOrderType = normalizeOrderType(walletOrder.getOrderType());
        if ("RECHARGE".equals(normalizedOrderType)) {
            com.chua.payment.support.dto.WalletRechargeDTO dto = new com.chua.payment.support.dto.WalletRechargeDTO();
            dto.setMerchantId(walletOrder.getMerchantId());
            dto.setUserId(walletOrder.getUserId());
            dto.setRechargeNo(walletOrder.getOrderNo());
            dto.setAmount(walletOrder.getAmount());
            dto.setOperator(operator);
            dto.setRemark(remark);
            walletAccountService.recharge(dto);
            return;
        }
        if ("TRANSFER".equals(normalizedOrderType)) {
            walletAccountService.transfer(
                    walletOrder.getMerchantId(),
                    walletOrder.getUserId(),
                    walletOrder.getRelatedUserId(),
                    walletOrder.getAmount(),
                    walletOrder.getOrderNo(),
                    operator,
                    remark);
            return;
        }
        if ("WITHDRAW".equals(normalizedOrderType)) {
            walletAccountService.withdraw(
                    walletOrder.getMerchantId(),
                    walletOrder.getUserId(),
                    walletOrder.getAmount(),
                    walletOrder.getOrderNo(),
                    operator,
                    remark);
            return;
        }
        throw new PaymentException("不支持的钱包订单类型: " + walletOrder.getOrderType());
    }

    private void validateOrderType(String orderType, WalletOrder walletOrder) {
        String normalizedOrderType = normalizeOrderType(orderType);
        if (!StringUtils.hasText(normalizedOrderType)) {
            return;
        }
        if (!normalizedOrderType.equals(normalizeOrderType(walletOrder.getOrderType()))) {
            throw new PaymentException("钱包回调类型和订单类型不匹配");
        }
    }

    private String normalizeOrderType(String orderType) {
        if (!StringUtils.hasText(orderType)) {
            return null;
        }
        String normalized = orderType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if ("RECHARGE".equals(normalized)) {
            return "RECHARGE";
        }
        if ("TRANSFER".equals(normalized)) {
            return "TRANSFER";
        }
        if ("WITHDRAW".equals(normalized)) {
            return "WITHDRAW";
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new PaymentException("钱包回调缺少状态");
        }
        String normalized = status.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (matches(normalized, "SUCCESS", "SUCCEED", "SUCCEEDED", "COMPLETED", "FINISHED", "DONE", "OK")) {
            return "SUCCESS";
        }
        if (matches(normalized, "PROCESSING", "PENDING", "HANDLING", "IN_PROGRESS", "WAITING")) {
            return "PROCESSING";
        }
        if (matches(normalized, "FAILED", "FAIL", "ERROR", "REJECTED", "CANCELLED", "CLOSED")) {
            return "FAILED";
        }
        return normalized;
    }

    private boolean matches(String value, String... candidates) {
        if (candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
