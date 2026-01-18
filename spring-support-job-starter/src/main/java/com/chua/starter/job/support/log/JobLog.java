package com.chua.starter.job.support.log;

/**
 * 任务日志接口
 * <p>
 * 定义任务执行日志的基本契约，提供默认实现的获取方式。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see DefaultJobLog
 */
public interface JobLog {

    /** 默认日志实例 */
    JobLog INSTANCE = new DefaultJobLog();

    /**
     * 获取默认日志实例
     *
     * @return 默认的{@link DefaultJobLog}实例
     */
    static JobLog getDefault() {
        return INSTANCE;
    }
}
