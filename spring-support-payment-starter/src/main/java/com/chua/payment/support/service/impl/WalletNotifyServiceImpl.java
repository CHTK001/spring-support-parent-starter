package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.WalletNotifyDTO;
import com.chua.payment.support.dto.WalletTransferOpDTO;
import com.chua.payment.support.dto.WalletWithdrawRequest;
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
 * 钱包异步回调处理服务实现，根据渠道回调通知更新钱包订单状态及账户余额。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class WalletNotifyServiceImpl implements WalletNotifyService {

    /** Wallet排序服务 */
    private final WalletOrderService walletOrderService;
    /** Walletaccount服务 */
    private final WalletAccountService walletAccountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理通知
     * @param orderType orderType
     * @param orderNo orderNo
     * @param thirdPartyOrderNo thirdPartyOrderNo
     * @param status status
     * @param payload payload
     * @param reason reason
     */
    public void handleNotify(WalletNotifyDTO dto) {
        if (!StringUtils.hasText(dto.orderNo())) {
            throw new PaymentException("钱包回调缺少订单号");
        }
        WalletOrder walletOrder = walletOrderService.getByOrderNo(dto.orderNo());
        if (walletOrder == null) {
            throw new PaymentException("钱包订单不存在: " + dto.orderNo());
        }
        validateOrderType(dto.orderType(), walletOrder);

        String normalizedStatus = normalizeStatus(dto.status());
        switch (normalizedStatus) {
            case "SUCCESS" -> handleSuccess(walletOrder, dto.thirdPartyOrderNo(), dto.payload(), dto.reason());
            case "PROCESSING" -> walletOrderService.markProcessing(dto.orderNo(), dto.thirdPartyOrderNo(), dto.payload());
            case "FAILED" -> walletOrderService.markFailed(dto.orderNo(), dto.payload(), StringUtils.hasText(dto.reason()) ? dto.reason() : "钱包回调失败");
            default -> throw new PaymentException("不支持的钱包回调状态: " + dto.status());
        }
    }

    /**
     * 处理Success
     * @param walletOrder walletOrder
     * @param thirdPartyOrderNo thirdPartyOrderNo
     * @param payload payload
     * @param reason reason
     */
    private void handleSuccess(WalletOrder walletOrder,
                               String thirdPartyOrderNo,
                               String payload,
                               String reason) {
        if (!"SUCCESS".equalsIgnoreCase(walletOrder.getStatus())) {
            applyAccountChange(walletOrder, reason);
        }
        walletOrderService.markSuccess(walletOrder.getOrderNo(), thirdPartyOrderNo, payload);
    }

    /** 应用AccountChange */
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
            walletAccountService.transfer(new WalletTransferOpDTO(
                    walletOrder.getMerchantId(),
                    walletOrder.getUserId(),
                    walletOrder.getRelatedUserId(),
                    walletOrder.getAmount(),
                    walletOrder.getOrderNo(),
                    operator,
                    remark));
            return;
        }
        if ("WITHDRAW".equals(normalizedOrderType)) {
            walletAccountService.withdraw(new WalletWithdrawRequest(
                    walletOrder.getMerchantId(),
                    walletOrder.getUserId(),
                    walletOrder.getAmount(),
                    walletOrder.getOrderNo(),
                    operator,
                    remark));
            return;
        }
        throw new PaymentException("不支持的钱包订单类型: " + walletOrder.getOrderType());
    }

    /** 校验OrderType */
    private void validateOrderType(String orderType, WalletOrder walletOrder) {
        String normalizedOrderType = normalizeOrderType(orderType);
        if (!StringUtils.hasText(normalizedOrderType)) {
            return;
        }
        if (!normalizedOrderType.equals(normalizeOrderType(walletOrder.getOrderType()))) {
            throw new PaymentException("钱包回调类型和订单类型不匹配");
        }
    }

    /** NormalizeOrderType */
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

    /** NormalizeStatus */
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

    /** Matches */
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

    /** FirstNonBlank */
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

