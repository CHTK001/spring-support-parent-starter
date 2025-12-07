package com.chua.starter.common.support.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.lang.management.ManagementFactory;

/**
 * 进程ID转换器
 * <p>
 * 用于在日志中输出当前进程ID。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
public class PidConverter extends ClassicConverter {

    private static final String PID = getPid();

    private static String getPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }

    @Override
    public String convert(ILoggingEvent event) {
        return PID;
    }
}
