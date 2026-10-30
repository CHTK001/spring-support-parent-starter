package com.chua.starter.common.support.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.lang.management.ManagementFactory;

/**
 * 进程标识转换器
 * <p>
 * 用于在日志中输出当前进程标识。
 * </p>
 *
 * @since 2024/01/01
 * @author CH
 */
public class PidConverter extends ClassicConverter {

    /**
     * PID
    */
    private static final String PID = getPid();

    /**
     * 获取Pid
     *
     * @return 获取pid的结果
     */
    private static String getPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }

    @Override
    /**
     * 转换
     * @param event 参数
     * @return 返回值
    */
    public String convert(ILoggingEvent event) {
        return PID;
    }
}
