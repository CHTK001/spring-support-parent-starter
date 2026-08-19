package com.chua.starter.job.support.log;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 异步日志写入器，包装多个 {@link JobLogSink}。
 *
 * <p>使用后台线程异步消费队列，避免阻塞 Netty 事件循环。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class AsyncLogWriter implements JobLogSink {

    /** 队列 */
    private final LinkedBlockingQueue<JobLogEvent> queue;
    /** Sinks */
    private final List<JobLogSink> sinks;
    /** Worker */
    private final Thread worker;
    private volatile boolean running = true;

    /**
     * 构造异步日志写入器。
     *
     * @param sinks     下游 sink 列表
     * @param queueSize 队列容量
     * @param threadName 后台线程名称
     */
    public AsyncLogWriter(List<JobLogSink> sinks, int queueSize, String threadName) {
        this.sinks = sinks;
        this.queue = new LinkedBlockingQueue<>(queueSize);
        this.worker = new Thread(this::drainLoop, threadName);
        this.worker.setDaemon(true);
        this.worker.start();
        log.info("[job] AsyncLogWriter 已启动 thread={} queueSize={} sinks={}",
                threadName, queueSize, sinks.size());
    }

    @Override
    /** 写入 */
    public void write(JobLogEvent event) {
        if (event == null || !running) {
            return;
        }
        if (!queue.offer(event)) {
            log.warn("[job] AsyncLogWriter 队列已满, 丢弃日志 event={}", event.jobName());
        }
    }

    /**
     * 关闭写入器，等待剩余日志写入完毕。
     */
    public void shutdown() {
        running = false;
        worker.interrupt();
        try {
            worker.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 消费剩余
        drain();
    }

    /** DrainLoop */
    private void drainLoop() {
        while (running) {
            try {
                JobLogEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    writeToSinks(event);
                    drain();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        drain();
    }

    /** Drain */
    private void drain() {
        JobLogEvent event;
        while ((event = queue.poll()) != null) {
            writeToSinks(event);
        }
    }

    /** 写入ToSinks */
    private void writeToSinks(JobLogEvent event) {
        for (JobLogSink sink : sinks) {
            try {
                sink.write(event);
            } catch (Throwable ex) {
                log.warn("[job] sink 写入失败 cause={}", ex.getMessage(), ex);
            }
        }
    }
}