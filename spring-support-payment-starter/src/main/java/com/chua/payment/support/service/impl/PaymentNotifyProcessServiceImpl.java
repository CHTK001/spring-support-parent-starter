package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.event.PaymentNotifyFailedEvent;
import com.chua.payment.support.event.PaymentNotifyReceivedEvent;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import com.chua.payment.support.service.PaymentNotifyService;
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
 * 支付回调处理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotifyProcessServiceImpl implements PaymentNotifyProcessService {

    private final PaymentNotifyLogMapper notifyLogMapper;
    private final PaymentNotifyErrorMapper notifyErrorMapper;
    private final PaymentNotifyService paymentNotifyService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentNotifyLog logNotify(String notifyType, Long merchantId, String channelType, String channelSubType,
                                       HttpServletRequest request, String body) {
        PaymentNotifyLog notifyLog = new PaymentNotifyLog();
        notifyLog.setNotifyType(notifyType);
        notifyLog.setMerchantId(merchantId);
        notifyLog.setChannelType(channelType);
        notifyLog.setChannelSubType(channelSubType);
        notifyLog.setRequestHeaders(extractHeaders(request));
        notifyLog.setRequestBody(body);
        notifyLog.setRequestParams(extractParams(request));
        notifyLog.setProcessStatus("PENDING");
        notifyLog.setRetryCount(0);
        notifyLog.setReceivedAt(LocalDateTime.now());
        notifyLog.setReceivedTime(LocalDateTime.now());
        notifyLogMapper.insert(notifyLog);

        eventPublisher.publishEvent(new PaymentNotifyReceivedEvent(
            this, null, notifyLog.getMerchantId(), notifyLog.getId(),
            notifyType, channelType, channelSubType,
            notifyLog.getOrderNo(), notifyLog.getRefundNo(), isSignVerified(notifyLog.getSignVerified())
        ));

        return notifyLog;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

    private String extractParams(HttpServletRequest request) {
        StringBuilder params = new StringBuilder();
        request.getParameterMap().forEach((key, values) -> {
            params.append(key).append("=").append(String.join(",", values)).append("&");
        });
        return params.toString();
    }

    private void dispatchNotify(PaymentNotifyLog notifyLog, boolean refundNotify) {
        if (notifyLog == null) {
            throw new PaymentException("回调日志不存在");
        }
        if (notifyLog.getChannelId() == null) {
            throw new PaymentException("回调日志缺少 channelId，无法重放");
        }
        String normalizedType = upper(firstNonBlank(notifyLog.getNotifyType(), refundNotify ? "REFUND" : "PAYMENT"));
        if (normalizedType.contains("WECHAT") || "WECHAT".equalsIgnoreCase(notifyLog.getChannelType())) {
            Map<String, String> headers = parseHeaders(notifyLog.getRequestHeaders());
            if (refundNotify || normalizedType.contains("REFUND")) {
                paymentNotifyService.handleWechatRefundNotify(
                        notifyLog.getChannelId(),
                        headers.get("Wechatpay-Serial"),
                        headers.get("Wechatpay-Timestamp"),
                        headers.get("Wechatpay-Nonce"),
                        headers.get("Wechatpay-Signature"),
                        headers.get("Wechatpay-Signature-Type"),
                        notifyLog.getRequestBody());
                return;
            }
            paymentNotifyService.handleWechatPayNotify(
                    notifyLog.getChannelId(),
                    headers.get("Wechatpay-Serial"),
                    headers.get("Wechatpay-Timestamp"),
                    headers.get("Wechatpay-Nonce"),
                    headers.get("Wechatpay-Signature"),
                    headers.get("Wechatpay-Signature-Type"),
                    notifyLog.getRequestBody());
            return;
        }
        if (normalizedType.contains("ALIPAY") || "ALIPAY".equalsIgnoreCase(notifyLog.getChannelType())) {
            paymentNotifyService.handleAlipayPayNotify(notifyLog.getChannelId(), parseParams(notifyLog.getRequestParams()));
            return;
        }
        throw new PaymentException("不支持的回调重放类型: " + notifyLog.getNotifyType());
    }

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

    private Boolean isSignVerified(Integer signVerified) {
        return signVerified != null && signVerified == 1;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
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

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private LocalDateTime calculateNextRetryTime(int retryCount) {
        int[] delays = {1, 5, 15, 30, 60};
        int delayMinutes = retryCount < delays.length ? delays[retryCount] : 60;
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
}
