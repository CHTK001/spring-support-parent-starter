package com.chua.payment.support.controller;

import com.chua.payment.support.dto.LogNotifyDTO;
import com.chua.payment.support.dto.WalletNotifyDTO;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import com.chua.payment.support.service.WalletNotifyService;
import com.chua.payment.support.service.WalletOrderService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
* 钱包异步回调控制器
* @author CH
* @since 4.0.0
 */
@Slf4j
@Hidden
@RestController
@ApiReturnFormatIgnore
@RequestMapping("/notify/wallet")
@RequiredArgsConstructor
public class WalletNotifyController {

    /** Wallet排序服务 */
    private final WalletOrderService walletOrderService;
    /** Walletnotify服务 */
    private final WalletNotifyService walletNotifyService;
    /** Paymentnotifyprocess服务 */
    private final PaymentNotifyProcessService paymentNotifyProcessService;
    /** Objectmapper */
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/{orderType}/{orderNo}", produces = MediaType.TEXT_PLAIN_VALUE)
    /**
    * 处理Wallet通知
    * @param orderType 订单类型
    * @param orderNo 订单no
    * @param params 参数
    * @param body 主体
    * @param request 请求
     */
    public String handleWalletNotify(@PathVariable String orderType,
                                     @PathVariable String orderNo,
                                     @RequestParam Map<String, String> params,
                                     @RequestBody(required = false) String body,
                                     HttpServletRequest request) {
        WalletOrder walletOrder = walletOrderService.getByOrderNo(orderNo);
        String payload = buildPayload(params, body);
        var notifyLog = paymentNotifyProcessService.logNotify(
                new LogNotifyDTO(
                        /**
                        * wallet通知类型。
                        * @param orderType 订单类型
                         */
                        walletNotifyType(orderType),
                        walletOrder != null ? walletOrder.getMerchantId() : null,
                        null,
                        "WALLET",
                        /**
                        * normalize订单类型。
                        * @param orderType 订单类型
                         */
                        normalizeOrderType(orderType),
                        orderNo,
                        null,
                        request,
                        payload));
        try {
            Map<String, Object> merged = mergePayload(params, body);
            walletNotifyService.handleNotify(
                    new WalletNotifyDTO(
                            orderType,
                            orderNo,
                            /**
                            * 第一个文本。
                            * @param merged 合并
                            * @param "thirdPartyOrderNo" "thirdparty订单no"
                            * @param "tradeNo" "贸易no"
                            * @param "trade_no" "贸易_no"
                            * @param "transactionId" "transactionid"
                            * @param "transaction_id" "transaction_标识"
                             */
                            firstText(merged, "thirdPartyOrderNo", "tradeNo", "trade_no", "transactionId", "transaction_id"),
                            /**
                            * 第一个文本。
                            * @param merged 合并
                            * @param "status" "状态"
                            * @param "tradeStatus" "贸易状态"
                            * @param "trade_status" "贸易_状态"
                            * @param "state" "状态"
                            * @param "resultStatus" "结果状态"
                            * @param "result_status" "结果_状态"
                            * @param "code" "编码"
                             */
                            firstText(merged, "status", "tradeStatus", "trade_status", "state", "resultStatus", "result_status", "code"),
                            payload,
                            firstText(merged, "reason", "message", "msg", "error", "errorMessage", "error_message", "errMsg")));
            paymentNotifyProcessService.markSuccess(notifyLog.getId(), "钱包回调处理成功");
            return "success";
        } catch (Exception e) {
            log.error("钱包回调处理失败: orderType={}, orderNo={}", orderType, orderNo, e);
            paymentNotifyProcessService.markFailedAndRecordError(
                    notifyLog.getId(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    stackTrace(e));
            return "failure";
        }
    }

    /**
    * Wallet通知类型
    *
    * @param orderType 订单类型
    * @return wallet通知类型的结果
     */
    private String walletNotifyType(String orderType) {
        String normalized = normalizeOrderType(orderType);
        if ("TRANSFER".equals(normalized)) {
            return "WALLET_TRANSFER";
        }
        if ("WITHDRAW".equals(normalized)) {
            return "WALLET_WITHDRAW";
        }
        return "WALLET_RECHARGE";
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
        return orderType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /**
    * 构建Payload
    *
    * @param params 参数
    * @param body 主体
    * @return 构建payload的结果
     */
    private String buildPayload(Map<String, String> params, String body) {
        if (StringUtils.hasText(body)) {
            return body;
        }
        try {
            return objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            return String.valueOf(params);
        }
    }

    /**
    * 合并Payload
    *
    * @param params 参数
    * @param body 主体
    * @return 合并payload的结果
     */
    private Map<String, Object> mergePayload(Map<String, String> params, String body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (params != null) {
            result.putAll(params);
        }
        if (StringUtils.hasText(body)) {
            try {
                Map<String, Object> bodyMap = objectMapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                result.putAll(bodyMap);
            } catch (Exception ignored) {
                result.put("body", body);
            }
        }
        return result;
    }

    /**
    * 第一个文本
    *
    * @param source 源
    * @param keys 钥匙
    * @return 第一个文本的结果
     */
    private String firstText(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
    * stack追踪
    *
    * @param throwable 抛出
    * @return stack追踪的结果
     */
    private String stackTrace(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}

