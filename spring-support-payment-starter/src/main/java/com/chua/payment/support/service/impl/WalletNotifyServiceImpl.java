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
     * @param orderType 订单类型
     * @param orderNo 订单no
     * @param thirdPartyOrderNo thirdparty订单no
     * @param status 状态
     * @param payload payload
     * @param reason ReasonMLmlml
     * @param dto dto
     */
    public void handleNotify(WalletNotifyDTO dto) {
        if (!StringUtils.hasText(dto.orderNo())) {
            /**
             * 薪酬异常。
             * @param "钱包回调缺少订单号" "钱包回调缺少订单号"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("钱包回调缺少订单号");
        }
        WalletOrder walletOrder = walletOrderService.getByOrderNo(dto.orderNo());
        if (walletOrder == null) {
            /**
             * 薪酬异常。
             * @param dto.orderNo( dto.订单no(
             * @return 薪酬异常的结果
             */
            throw new PaymentException("钱包订单不存在: " + dto.orderNo());
        }
        /**
         * 校验订单类型。
         * @param dto.orderType( dto.订单类型(
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
     * 处理成功
     * @param walletOrder wallet订单
     * @param thirdPartyOrderNo thirdparty订单no
     * @param payload payload
     * @param reason ReasonMLmlml
     */
    private void handleSuccess(WalletOrder walletOrder,
                               String thirdPartyOrderNo,
                               String payload,
                               String reason) {
        if (!"SUCCESS".equalsIgnoreCase(walletOrder.getStatus())) {
            /**
             * apply账户改变。
             * @param walletOrder wallet订单
             * @param reason ReasonMLmlml
             */
            applyAccountChange(walletOrder, reason);
        }
        walletOrderService.markSuccess(walletOrder.getOrderNo(), thirdPartyOrderNo, payload);
    }

    /**
     * 应用账户改变
     *
     * @param walletOrder wallet订单
     * @param reason ReasonMLmlml
     */
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
        /**
         * 薪酬异常。
         * @param walletOrder.getOrderType( walletorder.获取订单类型(
         * @return 薪酬异常的结果
         */
        throw new PaymentException("不支持的钱包订单类型: " + walletOrder.getOrderType());
    }

    /**
     * 校验订单类型
     *
     * @param orderType 订单类型
     * @param walletOrder wallet订单
     */
    private void validateOrderType(String orderType, WalletOrder walletOrder) {
        String normalizedOrderType = normalizeOrderType(orderType);
        if (!StringUtils.hasText(normalizedOrderType)) {
            return;
        }
        if (!normalizedOrderType.equals(normalizeOrderType(walletOrder.getOrderType()))) {
            /**
             * 薪酬异常。
             * @param "钱包回调类型和订单类型不匹配" "钱包回调类型和订单类型不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("钱包回调类型和订单类型不匹配");
        }
    }

    /**
     * normalize订单类型
     *
     * @param orderType 订单类型
     * @return normalize订单类型的结果
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
     * normalize状态
     *
     * @param status 状态
     * @return normalize状态的结果
     */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            /**
             * 薪酬异常。
             * @param "钱包回调缺少状态" "钱包回调缺少状态"
             * @return 薪酬异常的结果
             */
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
     * Matches
     *
     * @param value 值
     * @param candidates candidates
     * @return 匹配的结果
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
     * 第一个nonblank
     *
     * @param values 值
     * @return 第一个nonblank的结果
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

