package com.chua.starter.logger.client;

import com.chua.starter.logger.event.LoggerEvent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志事件采集入口：slf4j Appender 把事件投递到这里，批量线程再从队列取走推送。
 *
 * <p>两条防递归/防过载铁律：</p>
 * <ul>
 *   <li>logger 名以 {@code com.chua.starter.logger.} 或同步通道包
 *       {@code com.chua.common.support.network.sync} 开头的事件直接丢弃——
 *       本模块自身与传输层的日志不允许再进采集队列，否则会自我反馈；</li>
 *   <li>有界队列，满时丢弃新事件并计数，<b>绝不阻塞业务线程</b>。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
public class LoggerEventCollector {

    /**
     * 本模块包前缀（防递归）
     */
    private static final String SELF_PREFIX = "com.chua.starter.logger.";

    /**
     * 同步通道包前缀（传输层内部日志不采集，防反馈环路）
     */
    private static final String TRANSPORT_PREFIX = "com.chua.common.support.network.sync";

    private final BlockingQueue<LoggerEvent> queue;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong droppedByQueue = new AtomicLong();
    private final AtomicLong droppedByFilter = new AtomicLong();

    /**
     * @param capacity 队列容量
     */
    public LoggerEventCollector(int capacity) {
        this.queue = new ArrayBlockingQueue<>(Math.max(256, capacity));
    }

    /**
     * 投递事件（Appender 线程调用，永不阻塞、永不抛出）
     *
     * @param event 日志事件
     * @return 是否入队成功
     */
    public boolean offer(LoggerEvent event) {
        if (event == null) {
            return false;
        }
        String logger = event.getLogger();
        if (logger != null && (logger.startsWith(SELF_PREFIX) || logger.startsWith(TRANSPORT_PREFIX))) {
            droppedByFilter.incrementAndGet();
            return false;
        }
        accepted.incrementAndGet();
        if (queue.offer(event)) {
            return true;
        }
        droppedByQueue.incrementAndGet();
        return false;
    }

    /**
     * 取走一个事件（批量线程调用）
     *
     * @return 事件；队列空返回 null
     */
    public LoggerEvent poll() {
        return queue.poll();
    }

    /**
     * 带超时取走一个事件
     *
     * @param timeout 超时数值
     * @param unit    时间单位
     * @return 事件；超时返回 null
     * @throws InterruptedException 等待中被中断
     */
    public LoggerEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * 当前队列积压
     *
     * @return 积压条数
     */
    public int backlog() {
        return queue.size();
    }

    /**
     * 已接受事件数
     *
     * @return 计数
     */
    public long accepted() {
        return accepted.get();
    }

    /**
     * 因队列满被丢弃的事件数
     *
     * @return 计数
     */
    public long droppedByQueue() {
        return droppedByQueue.get();
    }

    /**
     * 因防递归过滤被丢弃的事件数
     *
     * @return 计数
     */
    public long droppedByFilter() {
        return droppedByFilter.get();
    }
}
