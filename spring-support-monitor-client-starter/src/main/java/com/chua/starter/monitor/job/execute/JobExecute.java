package com.chua.starter.monitor.job.execute;

import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.starter.monitor.job.TriggerParam;

/**
 * 作业执行
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobExecute {

    /**
     * 運行
     *
     * @param triggerParam 触发器参数
     * @return {@link BootResponse}
     */
    BootResponse run(TriggerParam triggerParam);
}
