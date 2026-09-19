package com.chua.starter.job.support.log;

/**
 * 日志落库抽象 (运行在 调度器 角色端)。
 *
 * <p>默认实现见 {@link StdoutJobLogSink}, 仅打印到 stdout;
 * 生产环境可基于 MyBatis-Plus / JDBC 实现覆盖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface JobLogSink {

    /**
     * 写入单条日志。
     *
     * @param event 日志事件
     */
    void write(JobLogEvent event);
}
