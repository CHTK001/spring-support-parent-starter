// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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

    /**
     * 队列
    */
    private final LinkedBlockingQueue<JobLogEvent> queue;
    /**
     * Sinks
    */
    private final List<JobLogSink> sinks;
    /**
     * 工人
    */
    private final Thread worker;
    private volatile boolean running = true; // running

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
        // 消费端是「阻塞式 JDBC 插入 + 文件写入」，正是虚拟线程的适用场景：JDBC 阻塞时
        // 会释放载体线程，不会因为一个慢 sink 就占住平台线程（原实现是一条常驻平台线程，
        // 队列积压时它与业务线程池争抢载体）。虚拟线程天然是 daemon，进程退出不会被它拖住。
        this.worker = Thread.ofVirtual().name(threadName).unstarted(this::drainLoop);
        this.worker.start();
        log.info("[job] AsyncLogWriter 已启动 thread={}(virtual={}) queueSize={} sinks={}",
                threadName, worker.isVirtual(), queueSize, sinks.size());
    }

    @Override
    /**
     * 写入
     * @param event 参数
     * @return 返回值
    */
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
        /**
         * drain。
         */
        drain();
    }

    /**
     * drain循环
    */
    private void drainLoop() {
        while (running) {
            try {
                JobLogEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    /**
                     * 写入转为sinks。
                     * @param event 事件
                     */
                    writeToSinks(event);
                    /**
                     * drain。
                     */
                    drain();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        /**
         * drain。
         */
        drain();
    }

    /**
     * Drain
    */
    private void drain() {
        JobLogEvent event;
        while ((event = queue.poll()) != null) {
            /**
             * 写入转为sinks。
             * @param event 事件
             */
            writeToSinks(event);
        }
    }

    /**
     * 写入转为sinks
     *
     * @param event 事件
     */
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