package com.chua.starter.job.test;

import com.chua.starter.job.support.annotation.Job;
import org.springframework.stereotype.Component;

/**
 * 测试用样例 handler, SINGLE 模式下会被单秒循环触发。
 *
 * @author CH
 */
@Component
public class DemoJob {

    /**
     * 调用次数, 由测试线程读取断言
     */
    private volatile int invokeCount = 0;

    /**
     * sample handler
     */
    @Job("demo")
    public void execute() {
        invokeCount++;
    }

    /**
     * 当前调用次数。
     *
     * @return 调用次数
     */
    public int getInvokeCount() {
        return invokeCount;
    }
}
