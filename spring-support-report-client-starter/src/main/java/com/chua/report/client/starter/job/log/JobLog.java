package com.chua.report.client.starter.job.log;

/**
 * 作业日志
 *
   * @版本 1.0.0
 * @since 2024/03/11
 * @author CH
 */
public interface JobLog{

    JobLog INSTANCE = new DefaultJobLog(); // INSTANCE

    /**
     * 获取默认值
     *
     * @return 获取默认的结果
     */
    static JobLog getDefault() {
        return INSTANCE;
    }

}
