package com.chua.starter.monitor.endpoint;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * redis端点
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/02
 */

@WebEndpoint(id = "thread")
public class ThreadEndpoint {
    @ReadOperation
    public ThreadInfo[] read() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return threadBean.dumpAllThreads(true, true);
    }

}
