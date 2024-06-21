package com.chua.starter.common.support.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.chua.common.support.constant.Projects;

/**
 * PidConverter类继承自ClassicConverter，旨在实现特定的转换功能。
 * 该类的具体作用是通过重写父类方法来实现进程ID（PID）的转换逻辑。
 *
 * @author CH
 * @since 2024/6/21
 */
public class PidConverter extends ClassicConverter {

    static final String PID = Projects.getPid();
    @Override
    public String convert(ILoggingEvent event) {
        return PID;
    }
}
