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

    /** 通知日志映射器 */
    private final PaymentNotifyLogMapper notifyLogMapper;
    /** 通知错误映射器 */
    private final PaymentNotifyErrorMapper notifyErrorMapper;
    /** Paymentnotify服务 */
    private final PaymentNotifyService paymentNotifyService;
    /** wechat薪酬分数通知服务 */
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
     * 日志通知
     * @param notifyType 通知类型
     * @param merchantId merchantid
     * @param channelId 通道标识
     * @param channelType 通道类型
     * @param channelSubType 通道sub类型
     * @param orderNo 订单no
     * @param refundNo 退税no
     * @param request 请求
     * @param body 主体
     * @param dto dto
     * @return 日志通知的结果
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
    /**
     * 处理Payment通知
     *
     * @param log 日志
     */
    public void processPaymentNotify(PaymentNotifyLog log) {
        try {
            log.setProcessStatus("PROCESSING");
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);

            /**
             * dispatch通知。
             * @param log 日志
             * @param false false
             */
            dispatchNotify(log, false);

            /**
             * mark成功。
             * @param log.getId( 日志.获取标识(
             */
            markSuccess(log.getId(), "支付回调处理成功");
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("处理支付回调失败", e);
            /**
             * mark失败andrecord错误。
             * @param log.getId( 日志.获取标识(
             */
            markFailedAndRecordError(log.getId(), e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理退税通知
     *
     * @param log 日志
     */
    public void processRefundNotify(PaymentNotifyLog log) {
        try {
            log.setProcessStatus("PROCESSING");
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);

            /**
             * dispatch通知。
             * @param log 日志
             * @param true true
             */
            dispatchNotify(log, true);

            /**
             * mark成功。
             * @param log.getId( 日志.获取标识(
             */
            markSuccess(log.getId(), "退款回调处理成功");
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("处理退款回调失败", e);
            /**
             * mark失败andrecord错误。
             * @param log.getId( 日志.获取标识(
             */
            markFailedAndRecordError(log.getId(), e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记成功
     *
     * @param logId 日志标识
     * @param result 结果
     */
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
    /**
     * 标记失败andrecord记录错误
     *
     * @param logId 日志标识
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     * @param errorStack 错误stack
     */
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
    /**
     * 重试失败通知
     *
     * @param errorId 错误标识
     */
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
                /**
                 * 薪酬异常。
                 * @param "回调日志不存在" "回调日志不存在"
                 * @return 薪酬异常的结果
                 */
                throw new PaymentException("回调日志不存在");
            }

            if ("PAYMENT".equals(error.getNotifyType())) {
                /**
                 * 处理薪酬通知。
                 * @param log 日志
                 */
                processPaymentNotify(log);
            } else if ("REFUND".equals(error.getNotifyType())) {
                /**
                 * 处理退税通知。
                 * @param log 日志
                 */
                processRefundNotify(log);
            } else {
                /**
                 * dispatch通知。
                 * @param log 日志
                 * @param false false
                 */
                dispatchNotify(log, false);
                /**
                 * mark成功。
                 * @param log.getId( 日志.获取标识(
                 */
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

    /**
     * extract头部
     *
     * @param request 请求
     * @return extract头部的结果
     */
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

    /**
     * extract参数
     *
     * @param request 请求
     * @return extract参数的结果
     */
    private String extractParams(HttpServletRequest request) {
        StringBuilder params = new StringBuilder();
        request.getParameterMap().forEach((key, values) -> {
            params.append(key).append("=").append(String.join(",", values)).append("&");
        });
        return params.toString();
    }

    /**
     * 分发通知
     *
     * @param notifyLog 通知日志
     * @param refundNotify 退税通知
     */
    private void dispatchNotify(PaymentNotifyLog notifyLog, boolean refundNotify) {
        if (notifyLog == null) {
            /**
             * 薪酬异常。
             * @param "回调日志不存在" "回调日志不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("回调日志不存在");
        }
        String normalizedType = upper(firstNonBlank(notifyLog.getNotifyType(), refundNotify ? "REFUND" : "PAYMENT"));
        if (normalizedType.contains("WALLET") || "WALLET".equalsIgnoreCase(notifyLog.getChannelType())) {
            /**
             * dispatchwallet通知。
             * @param notifyLog 通知日志
             */
            dispatchWalletNotify(notifyLog);
            return;
        }
        if (notifyLog.getChannelId() == null) {
            /**
             * 薪酬异常。
             * @param channelId，无法重放" 通道标识，无法重放"
             * @return 薪酬异常的结果
             */
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
        /**
         * 薪酬异常。
         * @param notifyLog.getNotifyType( 通知日志.获取通知类型(
         * @return 薪酬异常的结果
         */
        throw new PaymentException("不支持的回调重放类型: " + notifyLog.getNotifyType());
    }

    /**
     * 分发Wallet通知
     *
     * @param notifyLog 通知日志
     */
    private void dispatchWalletNotify(PaymentNotifyLog notifyLog) {
        Map<String, Object> payload = mergeNotifyPayload(notifyLog);
        walletNotifyService.handleNotify(
                new WalletNotifyDTO(
                        /**
                         * 第一个nonblank。
                         * @param notifyLog.getChannelSubType( 通知日志.获取通道subtype(
                         */
                        firstNonBlank(notifyLog.getChannelSubType(), deriveWalletOrderType(notifyLog.getNotifyType())),
                        notifyLog.getOrderNo(),
                        /**
                         * 第一个文本。
                         * @param payload payload
                         * @param "thirdPartyOrderNo" "thirdparty订单no"
                         * @param "tradeNo" "贸易no"
                         * @param "trade_no" "贸易_no"
                         * @param "transactionId" "transactionid"
                         * @param "transaction_id" "transaction_标识"
                         */
                        firstText(payload, "thirdPartyOrderNo", "tradeNo", "trade_no", "transactionId", "transaction_id"),
                        /**
                         * 第一个文本。
                         * @param payload payload
                         * @param "status" "状态"
                         * @param "tradeStatus" "贸易状态"
                         * @param "trade_status" "贸易_状态"
                         * @param "state" "状态"
                         * @param "resultStatus" "结果状态"
                         * @param "result_status" "结果_状态"
                         * @param "code" "编码"
                         */
                        firstText(payload, "status", "tradeStatus", "trade_status", "state", "resultStatus", "result_status", "code"),
                        /**
                         * 构建walletpayload。
                         * @param notifyLog 通知日志
                         */
                        buildWalletPayload(notifyLog),
                        firstText(payload, "reason", "message", "msg", "error", "errorMessage", "error_message", "errMsg")));
    }

    /**
     * 解析头部
     *
     * @param headers 头部
     * @return 解析头部的结果
     */
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

    /**
     * 解析参数
     *
     * @param params 参数
     * @return 解析参数的结果
     */
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

    /**
     * 合并通知Payload
     *
     * @param notifyLog 通知日志
     * @return 合并通知payload的结果
     */
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

    /**
     * 构建walletpayload
     *
     * @param notifyLog 通知日志
     * @return 构建walletpayload的结果
     */
    private String buildWalletPayload(PaymentNotifyLog notifyLog) {
        if (StringUtils.hasText(notifyLog.getRequestBody())) {
            return notifyLog.getRequestBody();
        }
        return notifyLog.getRequestParams();
    }

    /**
     * derivewallet订单类型
     *
     * @param notifyType 通知类型
     * @return derivewallet订单类型的结果
     */
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

    /**
     * 第一个文本
     *
     * @param source 源
     * @param keys Key
     * @return 第一个文本的结果
     */
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

    /**
     * 是否标志verified
     *
     * @param signVerified 标志verified
     * @return 是否标志verified的结果
     */
    private Boolean isSignVerified(Integer signVerified) {
        return signVerified != null && signVerified == 1;
    }

    /**
     * 大写
     *
     * @param value 值
     * @return 大写的结果
     */
    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
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

    /**
     * 获取stack追踪
     *
     * @param throwable 抛出
     * @return 获取stack追踪的结果
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * calculate下一个重试时间
     *
     * @param retryCount 重试数量
     * @return calculate下一个重试时间的结果
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        int[] delays = {1, 5, 15, 30, 60};
        int delayMinutes = retryCount < delays.length ? delays[retryCount] : 60;
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
}

