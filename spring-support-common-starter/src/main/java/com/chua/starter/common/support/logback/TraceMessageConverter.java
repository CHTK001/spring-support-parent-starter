package com.chua.starter.common.support.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chua.common.support.mdc.TraceContextHolder;

/**
 * 跟踪消息转换器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class TraceMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return TraceContextHolder.get();
    }
}
