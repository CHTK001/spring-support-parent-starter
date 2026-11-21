package com.chua.starter.logger.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.chua.starter.common.support.constant.MdcConstant;
import com.chua.starter.logger.event.LoggerEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Logback 桥接 Appender：把 {@link ILoggingEvent} 转成 {@link LoggerEvent} 交给采集器。
 *
 * @author CH
 * @since 4.0.0.51
 */
public class LogbackBridgeAppender extends AppenderBase<ILoggingEvent> {

    private final Consumer<LoggerEvent> sink;

    /**
     * @param name    Appender 名
     * @param sink    事件出口
     */
    public LogbackBridgeAppender(String name, Consumer<LoggerEvent> sink) {
        setName(name);
        this.sink = sink;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            Map<String, String> mdc = eventObject.getMDCPropertyMap();
            LoggerEvent event = new LoggerEvent()
                    .setTs(eventObject.getTimeStamp())
                    .setLevel(eventObject.getLevel() == null ? "INFO" : eventObject.getLevel().toString())
                    .setThread(eventObject.getThreadName())
                    .setLogger(eventObject.getLoggerName())
                    .setMessage(eventObject.getFormattedMessage())
                    .setMdc(mdc == null || mdc.isEmpty() ? null : new LinkedHashMap<>(mdc))
                    .setTraceId(mdc == null ? null : mdc.get(MdcConstant.TRACE_ID));
            IThrowableProxy proxy = eventObject.getThrowableProxy();
            if (proxy != null) {
                String stacktrace = ThrowableProxyUtil.asString(proxy);
                event.setStacktrace(StackTraces.truncate(stacktrace));
            }
            sink.accept(event);
        } catch (Exception ignored) {
            // 桥接失败不影响业务日志
        }
    }
}
