package com.chua.starter.job.support.log;

import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.function.BiConsumer;

/**
 * 单机 (SINGLE) 角色的执行日志落地桥。
 *
 * <p>执行结果一律以 {@link JobLogEvent} 形式发到本地总线, 而 {@code TASK_LOG} 的订阅者
 * {@code SchedulerSyncBinder} 只在 SCHEDULER 角色装配。单机角色既是调度方又是执行方,
 * 没有这个桥时自己发出的日志事件会没人消费, {@code sys_job_log} 恒空。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class LocalJobLogBinder implements InitializingBean, DisposableBean {

    /**
     * 事件总线
     */
    private final JobSyncBus bus;

    /**
     * 日志落地目标
     */
    private final JobLogSink sink;

    /**
     * 日志事件监听器
     */
    private final BiConsumer<String, Object> listener = (source, payload) -> onLog(source, payload);

    /**
     * 创建 单机日志桥 实例。
     *
     * @param bus   事件总线
     * @param sink  日志落地目标
     */
    public LocalJobLogBinder(JobSyncBus bus, JobLogSink sink) {
        this.bus = bus;
        this.sink = sink;
    }

    /**
     * 首条落地标记：只在第一次落库时打 INFO，之后走 debug，避免逐条刷屏。
     */
    private final java.util.concurrent.atomic.AtomicBoolean firstWritten = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * 收到日志事件后直接落库, 不经过远端上行链路。
     *
     * @param source  事件来源节点
     * @param payload 事件负载
     */
    private void onLog(String source, Object payload) {
        if (!(payload instanceof JobLogEvent event)) {
            return;
        }
        sink.write(event);
        if (firstWritten.compareAndSet(false, true)) {
            log.info("[job] 单机角色落日志通了 job={} status={}", event.jobName(), event.status());
            return;
        }
        log.debug("[job] 单机角色落日志 job={} source={}", event.jobName(), source);
    }

    @Override
    /**
     * 初始化
    */
    public void afterPropertiesSet() {
        bus.subscribe(JobSyncTopics.TASK_LOG, listener);
        log.info("[job] 单机角色日志桥已挂载 topic={} sink={}", JobSyncTopics.TASK_LOG, sink.getClass().getSimpleName());
    }

    @Override
    /**
     * 销毁
    */
    public void destroy() {
        bus.unsubscribe(JobSyncTopics.TASK_LOG, listener);
    }
}
