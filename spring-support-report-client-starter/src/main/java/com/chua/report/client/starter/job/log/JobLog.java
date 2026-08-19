package com.chua.report.client.starter.job.log;

/**
 * 作业日志
 *
 * @version 1.0.0
 * @since 2024/03/11
 * @author CH
 */
public interface JobLog{

    JobLog INSTANCE = new DefaultJobLog();

    /**
     * 获取默认值
     *
     */
    static JobLog getDefault() {
        return INSTANCE;
    }

}
