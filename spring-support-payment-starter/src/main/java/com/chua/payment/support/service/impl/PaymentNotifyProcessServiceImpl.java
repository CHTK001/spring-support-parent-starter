// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * 支付回调处理服务实现，统一路由微信、支付宝、钱包等渠道的异步通知到对应处理器。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotifyProcessServiceImpl implements PaymentNotifyProcessService {

    /**
     * 通知日志映射器
     */
    private final PaymentNotifyLogMapper notifyLogMapper;
    /**
     * 通知错误映射器
     */
    private final PaymentNotifyErrorMapper notifyErrorMapper;
    /**
     * 支付回调处理服务
     */
    private final PaymentNotifyService paymentNotifyService;
    /**
     * 微信支付分通知服务
     */
    private final WechatPayScoreNotifyService wechatPayScoreNotifyService;
    /**
     * 钱包通知处理服务
     */
    private final WalletNotifyService walletNotifyService;
    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;
    /**
     * 事件发布器
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 记录通知日志
     * @param notifyType 通知类型
     * @param merchantId 商户标识
     * @param channelId 渠道标识
     * @param channelType 渠道类型
     * @param channelSubType 渠道子类型
     * @param orderNo 订单号
     * @param refundNo 退款单号
     * @param request 请求
     * @param body 主体
     * @param dto dto
     * @return 记录到的通知日志
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
     * 处理支付回调
     *
     * @param log 日志
     */
    public void processPaymentNotify(PaymentNotifyLog log) {
        try {
            log.setProcessStatus("PROCESSING");
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);

            /**
             * 按渠道分发通知，false 表示支付通知
             * @param log 日志
             * @param false false
             */
            dispatchNotify(log, false);

            /**
             * 标记处理成功
             * @param log.getId() 日志标识
             */
            markSuccess(log.getId(), "支付回调处理成功");
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("处理支付回调失败", e);
            /**
             * 标记失败并记录异常
             * @param log.getId() 日志标识
             */
            markFailedAndRecordError(log.getId(), e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理退款回调
     *
     * @param log 日志
     */
    public void processRefundNotify(PaymentNotifyLog log) {
        try {
            log.setProcessStatus("PROCESSING");
            log.setProcessedAt(LocalDateTime.now());
            notifyLogMapper.updateById(log);

            /**
             * 按渠道分发通知，true 表示退款通知
             * @param log 日志
             * @param true true
             */
            dispatchNotify(log, true);

            /**
             * 标记处理成功
             * @param log.getId() 日志标识
             */
            markSuccess(log.getId(), "退款回调处理成功");
        } catch (Exception e) {
            PaymentNotifyProcessServiceImpl.log.error("处理退款回调失败", e);
            /**
             * 标记失败并记录异常
             * @param log.getId() 日志标识
             */
            markFailedAndRecordError(log.getId(), e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记通知处理成功
     *
     * @param logId 日志标识
     * @param result 处理结果
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
     * 标记通知处理失败并记录异常
     *
     * @param logId 日志标识
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     * @param errorStack 错误堆栈
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
     * 重试处理失败的通知
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
                throw new PaymentException("回调日志不存在");
            }

            if ("PAYMENT".equals(error.getNotifyType())) {
                /**
                 * 重新处理支付通知
                 * @param log 日志
                 */
                processPaymentNotify(log);
            } else if ("REFUND".equals(error.getNotifyType())) {
                /**
                 * 重新处理退款通知
                 * @param log 日志
                 */
                processRefundNotify(log);
            } else {
                /**
                 * 按渠道分发通知
                 * @param log 日志
                 * @param false false
                 */
                dispatchNotify(log, false);
                /**
                 * 标记处理成功
                 * @param log.getId() 日志标识
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
     * 提取请求头部文本
     *
     * @param request 请求
     * @return 提取出的请求头部文本
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
     * 提取请求参数文本
     *
     * @param request 请求
     * @return 提取出的请求参数文本
     */
    private String extractParams(HttpServletRequest request) {
        StringBuilder params = new StringBuilder();
        request.getParameterMap().forEach((key, values) -> {
            params.append(key).append("=").append(String.join(",", values)).append("&");
        });
        return params.toString();
    }

    /**
     * 按渠道与通知类型分发到对应处理器
     *
     * @param notifyLog 通知日志
     * @param refundNotify 是否为退款通知
     */
    private void dispatchNotify(PaymentNotifyLog notifyLog, boolean refundNotify) {
        if (notifyLog == null) {
            throw new PaymentException("回调日志不存在");
        }
        String normalizedType = upper(firstNonBlank(notifyLog.getNotifyType(), refundNotify ? "REFUND" : "PAYMENT"));
        if (normalizedType.contains("WALLET") || "WALLET".equalsIgnoreCase(notifyLog.getChannelType())) {
            /**
             * 分发钱包通知
             * @param notifyLog 通知日志
             */
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

    /**
     * 分发钱包通知
     *
     * @param notifyLog 通知日志
     */
    private void dispatchWalletNotify(PaymentNotifyLog notifyLog) {
        Map<String, Object> payload = mergeNotifyPayload(notifyLog);
        walletNotifyService.handleNotify(
                new WalletNotifyDTO(
                        /**
                         * 优先取渠道子类型，其次按通知类型推导钱包订单类型
                         * @param notifyLog.getChannelSubType() 通知日志的渠道子类型
                         */
                        firstNonBlank(notifyLog.getChannelSubType(), deriveWalletOrderType(notifyLog.getNotifyType())),
                        notifyLog.getOrderNo(),
                        /**
                         * 依次尝试多个第三方单号字段名
                         * @param payload 通知报文
                         */
                        firstText(payload, "thirdPartyOrderNo", "tradeNo", "trade_no", "transactionId", "transaction_id"),
                        /**
                         * 依次尝试多个状态字段名
                         * @param payload 通知报文
                         */
                        firstText(payload, "status", "tradeStatus", "trade_status", "state", "resultStatus", "result_status", "code"),
                        /**
                         * 构建钱包通知报文
                         * @param notifyLog 通知日志
                         */
                        buildWalletPayload(notifyLog),
                        firstText(payload, "reason", "message", "msg", "error", "errorMessage", "error_message", "errMsg")));
    }

    /**
     * 解析请求头部文本
     *
     * @param headers 头部文本
     * @return 解析出的请求头部
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
     * 解析查询参数文本
     *
     * @param params 参数文本
     * @return 解析出的参数映射
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
     * 合并通知报文：参数优先，其次合并 JSON 请求体
     *
     * @param notifyLog 通知日志
     * @return 合并后的通知报文
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
     * 构建钱包通知报文
     *
     * @param notifyLog 通知日志
     * @return 钱包通知报文
     */
    private String buildWalletPayload(PaymentNotifyLog notifyLog) {
        if (StringUtils.hasText(notifyLog.getRequestBody())) {
            return notifyLog.getRequestBody();
        }
        return notifyLog.getRequestParams();
    }

    /**
     * 按通知类型推导钱包订单类型
     *
     * @param notifyType 通知类型
     * @return 推导出的钱包订单类型
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
     * 依次尝试多个键名，返回第一个非空文本值
     *
     * @param source 报文
     * @param keys 候选键名
     * @return 第一个非空文本值
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
     * 判断签名是否已校验通过
     *
     * @param signVerified 签名校验标识
     * @return 签名是否已校验通过
     */
    private Boolean isSignVerified(Integer signVerified) {
        return signVerified != null && signVerified == 1;
    }

    /**
     * 转大写
     *
     * @param value 值
     * @return 转大写后的值
     */
    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
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

    /**
     * 获取异常堆栈文本
     *
     * @param throwable 异常
     * @return 异常堆栈文本
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 按退避策略计算下一次重试时间
     *
     * @param retryCount 已重试次数
     * @return 下一次重试时间
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        int[] delays = {1, 5, 15, 30, 60};
        int delayMinutes = retryCount < delays.length ? delays[retryCount] : 60;
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
}