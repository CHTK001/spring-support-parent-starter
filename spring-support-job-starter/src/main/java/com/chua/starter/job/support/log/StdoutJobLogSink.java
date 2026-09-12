package com.chua.starter.job.support.log;

import lombok.extern.slf4j.Slf4j;

/**
* 默认日志落库实现, 仅打印 stdout。
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
public class StdoutJobLogSink implements JobLogSink {

    @Override
    /** 写入 */
    public void write(JobLogEvent event) {
        if (event == null) {
            return;
        }
        log.info("[job-log] job={} status={} cost={}ms node={} executedAt={} exception={}",
                event.jobName(),
                event.status(),
                event.costMs(),
                event.nodeId(),
                event.executedAtMs(),
                event.exception());
    }
}
