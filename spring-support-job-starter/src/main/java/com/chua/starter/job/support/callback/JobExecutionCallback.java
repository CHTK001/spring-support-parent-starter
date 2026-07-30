package com.chua.starter.job.support.callback;

/**
 * 任务执行回调接口。
 * <p>
 * 用于在任务异常或准备重试时执行补偿、告警或扩展日志逻辑。
 * </p>
 */
public interface JobExecutionCallback {

    /**
     * 处理任务执行回调。
     *
     * @param context 回调上下文
     * @throws Exception 回调执行异常
     */
    void onCallback(JobExecutionCallbackContext context) throws Exception;
}
