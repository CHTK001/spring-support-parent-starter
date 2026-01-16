package com.chua.report.client.starter.job.execute;

import com.chua.common.support.protocol.request.ServletRequest;
import com.chua.common.support.protocol.request.ServletResponse;
import com.chua.report.client.starter.job.TriggerParam;

/**
 * 作业执行接口
 * <p>
 * 定义了作业执行的核心方法，用于处理定时任务的执行逻辑。
 * 实现类需要提供具体的作业执行实现。
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobExecute {

    /**
     * 运行作业
     * <p>
     * 执行具体的作业逻辑，根据触发参数处理业务逻辑并返回执行结果。
     *
     * @param request      请求对象，包含作业执行所需的请求信息
     * @param triggerParam 触发器参数，包含作业调度的相关参数信息
     * @return {@link ServletResponse} 作业执行结果响应
     */
    ServletResponse run(ServletRequest request, TriggerParam triggerParam);
}
