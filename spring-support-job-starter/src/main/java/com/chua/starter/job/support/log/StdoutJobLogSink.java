package com.chua.starter.job.support.log;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认日志落库实现, 仅打印 stdout。
 *
 * <p>作为 {@link JobLogSink} 的兜底实现: 未显式选择落库方式时打印日志即可,
 * 不会因为缺少数据库而失败。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@SpiDefault
@Spi({"stdout", "console"})
@Slf4j
public class StdoutJobLogSink implements JobLogSink {

    @Override
    /**
     * 写入
     * @param event 参数
     * @return 返回值
    */
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
