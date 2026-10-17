package com.chua.report.client.starter.job;

/**
 * 触发器参数类，用于封装与任务触发相关的参数信息。
 *
 * @param jobId                 任务标识
 * @param profile               配置文件
 * @param executorHandler       执行器处理代码的标识
 * @param executorParams        执行器的参数
 * @param executorBlockStrategy 执行器的阻塞策略
 * @param executorRouter        执行器的路由策略
 * @param executorTimeout       执行器超时时间
 * @param logId                 日志标识
 * @param logDateTime           日志生成时间戳
 * @param glueType              胶水类型（例如：脚本、JAR等）
 * @param glueSource            胶水代码源
 * @param glueUpdatetime        胶水最后更新时间
 * @param broadcastIndex        广播执行的当前索引
 * @param broadcastTotal        广播执行的总任务数
 * @author CH
 * @since 4.0.0
 */
public record TriggerParam(
        int jobId,
        String profile,
        String executorHandler,
        String executorParams,
        String executorBlockStrategy,
        String executorRouter,
        int executorTimeout,
        long logId,
        long logDateTime,
        String glueType,
        String glueSource,
        long glueUpdatetime,
        int broadcastIndex,
        int broadcastTotal) {
}
