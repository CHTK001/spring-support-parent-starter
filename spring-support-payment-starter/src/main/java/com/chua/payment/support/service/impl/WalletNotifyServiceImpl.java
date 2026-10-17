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

    /**
     * 钱包订单服务
     */
    private final WalletOrderService walletOrderService;
    /**
     * 钱包账户服务
     */
    private final WalletAccountService walletAccountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理钱包通知
     * @param orderType 订单类型
     * @param orderNo 订单号
     * @param thirdPartyOrderNo 第三方订单号
     * @param status 订单状态
     * @param payload 通知报文
     * @param reason 处理说明或失败原因
     * @param dto dto
     */
    public void handleNotify(WalletNotifyDTO dto) {
        if (!StringUtils.hasText(dto.orderNo())) {
            throw new PaymentException("钱包回调缺少订单号");
        }
        WalletOrder walletOrder = walletOrderService.getByOrderNo(dto.orderNo());
        if (walletOrder == null) {
            throw new PaymentException("钱包订单不存在: " + dto.orderNo());
        }
        /**
         * 校验通知类型与订单类型一致
         * @param dto.orderType() 通知中的订单类型
         */
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
     * 处理成功通知
     * @param walletOrder 钱包订单
     * @param thirdPartyOrderNo 第三方订单号
     * @param payload 通知报文
     * @param reason 处理说明
     */
    private void handleSuccess(WalletOrder walletOrder,
                               String thirdPartyOrderNo,
                               String payload,
                               String reason) {
        if (!"SUCCESS".equalsIgnoreCase(walletOrder.getStatus())) {
            /**
             * 应用账户资金变更
             * @param walletOrder 钱包订单
             * @param reason 处理说明
             */
            applyAccountChange(walletOrder, reason);
        }
        walletOrderService.markSuccess(walletOrder.getOrderNo(), thirdPartyOrderNo, payload);
    }

    /**
     * 按订单类型应用账户资金变更
     *
     * @param walletOrder 钱包订单
     * @param reason 处理说明
     */
    private void applyAccountChange(WalletOrder walletOrder, String reason) {
        String operator = firstNonBlank(walletOrder.getOperator(), "wallet-notify");
        String remark = firstNonBlank(reason, walletOrder.getRemark(), walletOrder.getOrderNo());
        String normalizedOrderType = normalizeOrderType(walletOrder.getOrderType());
        if ("RECHARGE".equals(normalizedOrderType)) {
            com.chua.payment.support.dto.WalletRechargeDTO dto = new com.chua.payment.support.dto.WalletRechargeDTO(
                    walletOrder.getMerchantId(),
                    walletOrder.getUserId(),
                    walletOrder.getOrderNo(),
                    null,
                    walletOrder.getAmount(),
                    operator,
                    remark);
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

    /**
     * 校验通知中的订单类型与钱包订单类型一致
     *
     * @param orderType 通知中的订单类型
     * @param walletOrder 钱包订单
     */
    private void validateOrderType(String orderType, WalletOrder walletOrder) {
        String normalizedOrderType = normalizeOrderType(orderType);
        if (!StringUtils.hasText(normalizedOrderType)) {
            return;
        }
        if (!normalizedOrderType.equals(normalizeOrderType(walletOrder.getOrderType()))) {
            throw new PaymentException("钱包回调类型和订单类型不匹配");
        }
    }

    /**
     * 规范化订单类型
     *
     * @param orderType 订单类型
     * @return 规范化后的订单类型
     */
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

    /**
     * 规范化回调状态，将渠道的各种别名归一
     *
     * @param status 原始状态
     * @return 归一后的状态
     */
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

    /**
     * 判断值是否匹配任一候选
     *
     * @param value 待匹配值
     * @param candidates 候选值
     * @return 是否匹配
     */
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

    /**
     * 返回第一个非空白值
     *
     * @param values 候选值
     * @return 第一个非空白值
     */
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
