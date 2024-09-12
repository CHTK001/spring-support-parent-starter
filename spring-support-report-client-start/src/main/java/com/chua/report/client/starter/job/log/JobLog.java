package com.chua.report.client.starter.job.log;

import com.chua.common.support.log.Log;

/**
 * 作业日志
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobLog extends Log{

    JobLog INSTANCE = new DefaultJobLog();

    /**
     * 获取默认值
     *
     * @return {@link Log}
     */
    static JobLog getDefault() {
        return INSTANCE;
    }

}
