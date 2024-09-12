package com.chua.report.client.starter.job.execute;

import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.report.client.starter.job.TriggerParam;

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
     * @param request
     * @param triggerParam 触发器参数
     * @return {@link Response}
     */
    Response run(Request request, TriggerParam triggerParam);
}
