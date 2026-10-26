package com.chua.starter.hotsport.support.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LogRecord;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 应用日志采集器 — 把宿主进程**真实**产生的 logback 日志写入 APM 存储。
 *
 * <p>这是「日志」页面与「实时日志」页面唯一的真实数据来源：不接这个 appender，
 * 存储里的日志永远是 0 条。采集到的每条记录都来自宿主真实的一次日志输出，
 * 级别、logger、类名、方法名、消息、traceId 全部原样透传，不做任何补充或模拟。</p>
 *
 * <p>三条硬性约束（APM 是观测组件，绝不允许影响宿主）：</p>
 * <ol>
 *   <li>任何异常都在内部吞掉，只做 {@code debug} 级别的自诊断，绝不外抛；</li>
 *   <li>用 ThreadLocal 防重入，避免「记录日志 → 触发采集 → 又记录日志」的递归；</li>
 *   <li>不采集 APM 自身与存储实现的日志，防止自我放大。</li>
 * </ol>
 *
 * @author CH
 * @since 4.1.0
 */
public class ApmLogAppender extends AppenderBase<ILoggingEvent> {

    /**
     * 与请求链路关联的 MDC 键：请求过滤器写入，采集器读取。
     */
    public static final String MDC_TRACE_ID = "hotsportTraceId";

    /**
     * 默认 appender 名称。
     */
    public static final String APPENDER_NAME = "HOTSPORT_APM_LOG";

    /**
     * 单条消息最大长度（超出截断），防止一条巨型异常栈把存储打爆。
     */
    private static final int DEFAULT_MAX_MESSAGE = 4096;

    /**
     * 不采集的 logger 前缀（APM 自身 / 存储实现），避免自我放大。
     */
    private static final String[] EXCLUDED_LOGGER_PREFIXES = {
            "com.chua.starter.hotsport.support.config.ApmLog",
            "com.chua.starter.hotsport.support.service.ApmQueryService",
            "com.chua.runtime.apm.storage"
    };

    /**
     * 传输层自噪声：APM 自己的 SSE 端点在客户端正常关闭连接时，
     * 容器会回调一次「响应已中断 / 客户端已断开」，被全局异常通知器记成 WARN。
     * 这类记录与被观测业务无关，属于观测组件自身的传输噪声，不入库。
     */
    private static final String[] SELF_NOISE_MARKERS = {
            "流式响应已中断",
            "disconnected client",
            "Response not usable after response errors"
    };

    /**
     * 自噪声必须同时命中该 URI 片段才丢弃，避免误伤真实业务异常。
     */
    private static final String SELF_NOISE_URI = "/api/apm/stream";

    /**
     * 最低采集级别。
     */
    private final Level minLevel;

    /**
     * 是否采集调用方类名/方法名（{@code getCallerData()} 有一定开销）。
     */
    private final boolean captureCaller;

    /**
     * 单条消息最大长度。
     */
    private final int maxMessageLength;

    /**
     * 防重入标记。
     */
    private final ThreadLocal<Boolean> inAppend = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 构造采集器。
     *
     * @param minLevel         最低采集级别（如 {@code INFO}）
     * @param captureCaller    是否采集调用方类名/方法名
     * @param maxMessageLength 单条消息最大长度
     */
    public ApmLogAppender(String minLevel, boolean captureCaller, int maxMessageLength) {
        this.minLevel = Level.toLevel(minLevel, Level.INFO);
        this.captureCaller = captureCaller;
        this.maxMessageLength = maxMessageLength > 0 ? maxMessageLength : DEFAULT_MAX_MESSAGE;
        setName(APPENDER_NAME);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || Boolean.TRUE.equals(inAppend.get())) {
            return;
        }
        inAppend.set(Boolean.TRUE);
        try {
            if (!accept(event)) {
                return;
            }
            ApmStorage storage = HotsportProperties.storage();
            if (storage == null) {
                return;
            }
            storage.appendLog(toRecord(event));
        } catch (Throwable ignore) {
            // 观测组件永不阻断宿主，且此处绝不能再打日志（会造成递归）
        } finally {
            inAppend.set(Boolean.FALSE);
        }
    }

    /**
     * 判断这条日志是否采集。
     *
     * @param event 日志事件
     * @return 是否采集
     */
    private boolean accept(ILoggingEvent event) {
        if (event.getLevel() == null || !event.getLevel().isGreaterOrEqual(minLevel)) {
            return false;
        }
        String logger = event.getLoggerName();
        if (logger != null) {
            for (String prefix : EXCLUDED_LOGGER_PREFIXES) {
                if (logger.startsWith(prefix)) {
                    return false;
                }
            }
        }
        return !isSelfTransportNoise(event);
    }

    /**
     * 判断是否为 APM 自身 SSE 端点产生的传输噪声。
     *
     * @param event 日志事件
     * @return true 表示应丢弃
     */
    private boolean isSelfTransportNoise(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null || !message.contains(SELF_NOISE_URI)) {
            return false;
        }
        for (String marker : SELF_NOISE_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 日志事件 → 存储记录（字段全部来自真实日志事件）。
     *
     * @param event 日志事件
     * @return 存储记录
     */
    private LogRecord toRecord(ILoggingEvent event) {
        String className = null;
        String methodName = null;
        if (captureCaller) {
            try {
                StackTraceElement[] caller = event.getCallerData();
                if (caller != null && caller.length > 0) {
                    className = caller[0].getClassName();
                    methodName = caller[0].getMethodName();
                }
            } catch (Throwable ignore) {
                // 取不到调用方信息就留空，不影响主流程
            }
        }
        String message = event.getFormattedMessage();
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            message = message + " | " + throwable.getClassName() + ": " + throwable.getMessage()
                    + "\n" + ThrowableProxyUtil.asString(throwable);
        }
        if (message != null && message.length() > maxMessageLength) {
            message = message.substring(0, maxMessageLength) + "...(truncated)";
        }
        return LogRecord.builder()
                .timestamp(event.getTimeStamp())
                .level(event.getLevel().toString())
                .logger(event.getLoggerName())
                .className(className)
                .methodName(methodName)
                .message(message)
                .traceId(MDC.get(MDC_TRACE_ID))
                .build();
    }

    @Override
    public void start() {
        try {
            if (getContext() == null && LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
                setContext(ctx);
            }
            super.start();
            attachToRoot();
        } catch (Throwable ignore) {
            // 采集器挂不上也不影响宿主
        }
    }

    @Override
    public void stop() {
        try {
            detachFromRoot();
        } catch (Throwable ignore) {
            // 忽略
        }
        super.stop();
    }

    /**
     * 挂到 root logger。
     */
    private void attachToRoot() {
        Logger root = rootLogger();
        if (root != null && !root.isAttached(this)) {
            root.addAppender(this);
        }
    }

    /**
     * 从 root logger 摘除。
     */
    private void detachFromRoot() {
        Logger root = rootLogger();
        if (root != null) {
            root.detachAppender(this);
        }
    }

    /**
     * 取 root logger。
     *
     * @return root logger，非 logback 环境返回 null
     */
    private Logger rootLogger() {
        if (getContext() instanceof LoggerContext ctx) {
            return ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        }
        return null;
    }
}
