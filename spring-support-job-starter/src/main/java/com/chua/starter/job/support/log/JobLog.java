package com.chua.starter.job.support.log;

/**
 * 作业日志接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobLog {

    JobLog INSTANCE = new DefaultJobLog();

    /**
     * 获取默认实例
     *
     * @return 默认日志实例
     */
    static JobLog getDefault() {
        return INSTANCE;
    }
}
