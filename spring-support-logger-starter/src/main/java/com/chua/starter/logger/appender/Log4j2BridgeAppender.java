package com.chua.starter.logger.appender;

import com.chua.starter.common.support.constant.MdcConstant;
import com.chua.starter.logger.event.LoggerEvent;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Log4j2 桥接 Appender：把 {@link LogEvent} 转成 {@link LoggerEvent} 交给采集器。
 *
 * @author CH
 * @since 4.0.0.51
 */
public class Log4j2BridgeAppender extends AbstractAppender {

    private final Consumer<LoggerEvent> sink;

    /**
     * @param name Appender 名
     * @param sink 事件出口
     */
    public Log4j2BridgeAppender(String name, Consumer<LoggerEvent> sink) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.sink = sink;
    }

    @Override
    public void append(LogEvent event) {
        try {
            Map<String, String> contextData = event.getContextData() == null
                    ? null : event.getContextData().toMap();
            LoggerEvent target = new LoggerEvent()
                    .setTs(event.getTimeMillis())
                    .setLevel(event.getLevel() == null ? "INFO" : event.getLevel().name())
                    .setThread(event.getThreadName())
                    .setLogger(event.getLoggerName())
                    .setMessage(event.getMessage() == null ? "" : event.getMessage().getFormattedMessage())
                    .setMdc(contextData == null || contextData.isEmpty() ? null : new HashMap<>(contextData))
                    .setTraceId(contextData == null ? null : contextData.get(MdcConstant.TRACE_ID));
            if (event.getThrown() != null) {
                target.setStacktrace(StackTraces.stacktraceOf(event.getThrown()));
            }
            sink.accept(target);
        } catch (Exception ignored) {
            // 桥接失败不影响业务日志
        }
    }
}
