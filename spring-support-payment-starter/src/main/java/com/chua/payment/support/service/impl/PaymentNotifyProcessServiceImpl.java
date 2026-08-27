package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.LogNotifyDTO;
import com.chua.payment.support.dto.WalletNotifyDTO;
import com.chua.payment.support.dto.WechatPayNotifyDTO;
import com.chua.payment.support.dto.WechatPayScoreNotifyDTO;
import com.chua.payment.support.dto.WechatRefundNotifyDTO;
import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.event.PaymentNotifyFailedEvent;
import com.chua.payment.support.event.PaymentNotifyReceivedEvent;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import com.chua.payment.support.service.PaymentNotifyService;
import com.chua.payment.support.service.WalletNotifyService;
import com.chua.payment.support.service.WechatPayScoreNotifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * 支付回调处理服务实现，统一路由微信/支付宝/钱包等渠道的异步通知到对应处理器。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotifyProcessServiceImpl implements PaymentNotifyProcessService {

    /** Notify日志mapper */
    private final PaymentNotifyLogMapper notifyLogMapper;
    /** Notify错误mapper */
    private final PaymentNotifyErrorMapper notifyErrorMapper;
    /** Paymentnotify服务 */
    private final PaymentNotifyService paymentNotifyService;
    /** WechatPAY分数notify服务 */
    private final WechatPayScoreNotifyService wechatPayScoreNotifyService;
    /** Walletnotify服务 */
    private final WalletNotifyService walletNotifyService;
    /** Objectmapper */
    private final ObjectMapper objectMapper;
    /** 事件publisher */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * Log通知
     * @param notifyType notifyType
     * @param merchantId merchantId
     * @param channelId channelId
     * @param channelType channelType
     * @param channelSubType channelSubType
     * @param orderNo orderNo
     * @param refundNo refundNo
     * @param request request
     * @param body body
     */
    public PaymentNotifyLog logNotify(LogNotifyDTO dto) {
        PaymentNotifyLog notifyLog = new PaymentNotifyLog();
        notifyLog.setNotifyType(dto.notifyType());
        notifyLog.setMerchantId(dto.merchantId());
        notifyLog.setChannelId(dto.channelId());
        notifyLog.setChannelType(dto.channelType());
        notifyLog.setChannelSubType(dto.channelSubType());
        notifyLog.setOrderNo(dto.orderNo());
        notifyLog.setRefundNo(dto.refundNo());
        notifyLog.setRequestHeaders(extractHeaders(dto.request()));
        notifyLog.setRequestBody(dto.body());
        notifyLog.setRequestParams(extractParams(dto.request()));
        notifyLog.setProcessStatus("PENDING");
        notifyLog.setRetryCount(0);
        notifyLog.setReceivedAt(LocalDateTime.now());
        notifyLog.setReceivedTime(LocalDateTime.now());
        notifyLogMapper.insert(notifyLog);

        eventPublisher.publishEvent(new PaymentNotifyReceivedEvent(
            this, null, notifyLog.getMerchantId(), notifyLog.getId(),
            dto.notifyType(), dto.channelType(), dto.channelSubType(),
            notifyLog.getOrderNo(), notifyLog.getRefundNo(), isSignVerified(notifyLog.getSignVerified())
        ));

        return notifyLog;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 处理Payment通知 */
    public void processPaymentNotify(PaymentNotifyLog log) {
        try {
            log.setProcessStatus("PROCESSING");
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);

            dispatchNotify(log, false);

            markSuccess(log.getId(), "支付回调处理成功");
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("处理支付回调失败", e);
            markFailedAndRecordError(log.getId(), e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 处理Refund通知 */
    public void processRefundNotify(PaymentNotifyLog log) {
        try {
            log.setProcessStatus("PROCESSING");
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);

            dispatchNotify(log, true);

            markSuccess(log.getId(), "退款回调处理成功");
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("处理退款回调失败", e);
            markFailedAndRecordError(log.getId(), e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 标记Success */
    public void markSuccess(Long logId, String result) {
        PaymentNotifyLog log = notifyLogMapper.selectById(logId);
        if (log != null) {
            log.setProcessStatus("SUCCESS");
            log.setProcessResult(result);
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 标记FailedAndRecord记录错误 */
    public void markFailedAndRecordError(Long logId, String errorType, String errorMessage, String errorStack) {
        PaymentNotifyLog log = notifyLogMapper.selectById(logId);
        if (log == null) {
            return;
        }

        log.setProcessStatus("FAILED");
        log.setErrorMessage(errorMessage);
        log.setRetryCount(log.getRetryCount() + 1);
        notifyLogMapper.updateById(log);

        PaymentNotifyError error = new PaymentNotifyError();
        error.setNotifyLogId(logId);
        error.setNotifyType(log.getNotifyType());
        error.setTenantId(null);
        error.setMerchantId(log.getMerchantId());
        error.setOrderNo(log.getOrderNo());
        error.setRefundNo(log.getRefundNo());
        error.setErrorType(errorType);
        error.setErrorMessage(errorMessage);
        error.setErrorStack(errorStack);
        error.setRequestData(log.getRequestBody());
        error.setRetryCount(0);
        error.setMaxRetryCount(5);
        error.setNextRetryTime(calculateNextRetryTime(0));
        error.setStatus("PENDING");
        notifyErrorMapper.insert(error);

        eventPublisher.publishEvent(new PaymentNotifyFailedEvent(
            this, null, log.getMerchantId(), logId,
            error.getId(), log.getNotifyType(), log.getOrderNo(), log.getRefundNo(),
            errorType, errorMessage
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** RetryFailed通知 */
    public void retryFailedNotify(Long errorId) {
        PaymentNotifyError error = notifyErrorMapper.selectById(errorId);
        if (error == null || !"PENDING".equals(error.getStatus())) {
            return;
        }

        if (error.getRetryCount() >= error.getMaxRetryCount()) {
            error.setStatus("ABANDONED");
            error.setRemark("超过最大重试次数");
            notifyErrorMapper.updateById(error);
            return;
        }

        error.setStatus("PROCESSING");
        notifyErrorMapper.updateById(error);

        try {
            PaymentNotifyLog log = notifyLogMapper.selectById(error.getNotifyLogId());
            if (log == null) {
                throw new PaymentException("回调日志不存在");
            }

            if ("PAYMENT".equals(error.getNotifyType())) {
                processPaymentNotify(log);
            } else if ("REFUND".equals(error.getNotifyType())) {
                processRefundNotify(log);
            } else {
                dispatchNotify(log, false);
                markSuccess(log.getId(), "回调重试处理成功");
            }

            error.setStatus("RESOLVED");
            error.setResolvedAt(LocalDateTime.now());
            notifyErrorMapper.updateById(error);
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("重试回调处理失败", e);
            error.setStatus("PENDING");
            error.setRetryCount(error.getRetryCount() + 1);
            error.setNextRetryTime(calculateNextRetryTime(error.getRetryCount()));
            error.setErrorMessage(e.getMessage());
            notifyErrorMapper.updateById(error);
        }
    }

    /** ExtractHeaders */
    private String extractHeaders(HttpServletRequest request) {
        StringBuilder headers = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            headers.append(name).append(": ").append(value).append("\n");
        }
        return headers.toString();
    }

    /** ExtractParams */
    private String extractParams(HttpServletRequest request) {
        StringBuilder params = new StringBuilder();
        request.getParameterMap().forEach((key, values) -> {
            params.append(key).append("=").append(String.join(",", values)).append("&");
        });
        return params.toString();
    }

    /** 分发通知 */
    private void dispatchNotify(PaymentNotifyLog notifyLog, boolean refundNotify) {
        if (notifyLog == null) {
            throw new PaymentException("回调日志不存在");
        }
        String normalizedType = upper(firstNonBlank(notifyLog.getNotifyType(), refundNotify ? "REFUND" : "PAYMENT"));
        if (normalizedType.contains("WALLET") || "WALLET".equalsIgnoreCase(notifyLog.getChannelType())) {
            dispatchWalletNotify(notifyLog);
            return;
        }
        if (notifyLog.getChannelId() == null) {
            throw new PaymentException("回调日志缺少 channelId，无法重放");
        }
        if (normalizedType.contains("WECHAT") || "WECHAT".equalsIgnoreCase(notifyLog.getChannelType())) {
            Map<String, String> headers = parseHeaders(notifyLog.getRequestHeaders());
            if (normalizedType.contains("PAYSCORE")) {
                wechatPayScoreNotifyService.handleNotify(
                        new WechatPayScoreNotifyDTO(
                                notifyLog.getChannelId(),
                                notifyLog.getOrderNo(),
                                headers.get("Wechatpay-Serial"),
                                headers.get("Wechatpay-Timestamp"),
                                headers.get("Wechatpay-Nonce"),
                                headers.get("Wechatpay-Signature"),
                                headers.get("Wechatpay-Signature-Type"),
                                notifyLog.getRequestBody()));
                return;
            }
            if (refundNotify || normalizedType.contains("REFUND")) {
                paymentNotifyService.handleWechatRefundNotify(
                        new WechatRefundNotifyDTO(
                                notifyLog.getChannelId(),
                                notifyLog.getRefundNo(),
                                headers.get("Wechatpay-Serial"),
                                headers.get("Wechatpay-Timestamp"),
                                headers.get("Wechatpay-Nonce"),
                                headers.get("Wechatpay-Signature"),
                                headers.get("Wechatpay-Signature-Type"),
                                notifyLog.getRequestBody()));
                return;
            }
            paymentNotifyService.handleWechatPayNotify(
                    new WechatPayNotifyDTO(
                            notifyLog.getChannelId(),
                            notifyLog.getOrderNo(),
                            headers.get("Wechatpay-Serial"),
                            headers.get("Wechatpay-Timestamp"),
                            headers.get("Wechatpay-Nonce"),
                            headers.get("Wechatpay-Signature"),
                            headers.get("Wechatpay-Signature-Type"),
                            notifyLog.getRequestBody()));
            return;
        }
        if (normalizedType.contains("ALIPAY") || "ALIPAY".equalsIgnoreCase(notifyLog.getChannelType())) {
            paymentNotifyService.handleAlipayPayNotify(
                    notifyLog.getChannelId(),
                    notifyLog.getOrderNo(),
                    parseParams(notifyLog.getRequestParams()));
            return;
        }
        throw new PaymentException("不支持的回调重放类型: " + notifyLog.getNotifyType());
    }

    /** 分发Wallet通知 */
    private void dispatchWalletNotify(PaymentNotifyLog notifyLog) {
        Map<String, Object> payload = mergeNotifyPayload(notifyLog);
        walletNotifyService.handleNotify(
                new WalletNotifyDTO(
                        firstNonBlank(notifyLog.getChannelSubType(), deriveWalletOrderType(notifyLog.getNotifyType())),
                        notifyLog.getOrderNo(),
                        firstText(payload, "thirdPartyOrderNo", "tradeNo", "trade_no", "transactionId", "transaction_id"),
                        firstText(payload, "status", "tradeStatus", "trade_status", "state", "resultStatus", "result_status", "code"),
                        buildWalletPayload(notifyLog),
                        firstText(payload, "reason", "message", "msg", "error", "errorMessage", "error_message", "errMsg")));
    }

    /** 解析Headers */
    private Map<String, String> parseHeaders(String headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(headers)) {
            return result;
        }
        for (String line : headers.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            result.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return result;
    }

    /** 解析Params */
    private Map<String, String> parseParams(String params) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(params)) {
            return result;
        }
        for (String pair : params.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                result.put(pair, "");
                continue;
            }
            result.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return result;
    }

    /** 合并通知Payload */
    private Map<String, Object> mergeNotifyPayload(PaymentNotifyLog notifyLog) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(parseParams(notifyLog.getRequestParams()));
        if (StringUtils.hasText(notifyLog.getRequestBody())) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> bodyMap = objectMapper.readValue(notifyLog.getRequestBody(), LinkedHashMap.class);
                result.putAll(bodyMap);
            } catch (Exception ignored) {
                result.put("body", notifyLog.getRequestBody());
            }
        }
        return result;
    }

    /** 构建WalletPayload */
    private String buildWalletPayload(PaymentNotifyLog notifyLog) {
        if (StringUtils.hasText(notifyLog.getRequestBody())) {
            return notifyLog.getRequestBody();
        }
        return notifyLog.getRequestParams();
    }

    /** DeriveWalletOrderType */
    private String deriveWalletOrderType(String notifyType) {
        String normalized = upper(notifyType);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("TRANSFER")) {
            return "TRANSFER";
        }
        if (normalized.contains("WITHDRAW")) {
            return "WITHDRAW";
        }
        return "RECHARGE";
    }

    /** FirstText */
    private String firstText(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /** 是否SignVerified */
    private Boolean isSignVerified(Integer signVerified) {
        return signVerified != null && signVerified == 1;
    }

    /** Upper */
    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
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

    /** 获取StackTrace */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /** CalculateNextRetryTime */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        int[] delays = {1, 5, 15, 30, 60};
        int delayMinutes = retryCount < delays.length ? delays[retryCount] : 60;
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
}

