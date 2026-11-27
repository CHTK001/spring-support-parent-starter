package com.chua.starter.collect.engine;

import com.chua.starter.collect.properties.CollectProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 本实例的采集任务准入控制。
 *
 * <p>覆盖手动、定时和草稿执行，包含建任务、执行与收尾阶段。容量用尽立即拒绝，
 * 不在虚拟线程或内存队列里无限等待；流程自己的跨节点并发限制仍由流程服务维护。
 * 调低上限不取消已接收任务，新的任务等待在途数量低于新上限后才能进入。</p>
 */
@Component
@RequiredArgsConstructor
public class CollectExecutionAdmission {

    /** 运行参数，由参数服务负责校验和更新。 */
    private final CollectProperties properties;

    /** 当前已接收且尚未收尾的任务数。 */
    private final AtomicInteger active = new AtomicInteger();

    /** 本进程启动以来因容量不足被拒绝的次数。 */
    private final LongAdder rejected = new LongAdder();

    /** 关闭后不再接收新任务。 */
    private volatile boolean accepting = true;

    /**
     * 接收一个任务并返回必须释放的名额。
     *
     * @return 独占名额，close 可重复调用
     * @throws IllegalStateException 实例正在关闭或容量已满
     */
    public synchronized Permit acquire() {
        if (!accepting) {
            throw new IllegalStateException("采集执行器正在关闭，请稍后重试");
        }
        int limit = Math.max(1, properties.getMaxConcurrentTasks());
        if (active.get() >= limit) {
            rejected.increment();
            throw new IllegalStateException("采集实例并发任务数已达上限 " + limit + "，请稍后重试");
        }
        active.incrementAndGet();
        return new Permit(active);
    }

    /** @return 本实例已接收的在途任务数 */
    public int activeCount() {
        return active.get();
    }

    /** @return 因容量不足被拒绝的累计次数 */
    public long rejectedCount() {
        return rejected.sum();
    }

    /** 停机时关闭准入；已有名额继续由各任务的 finally 释放。 */
    public synchronized void stopAccepting() {
        accepting = false;
    }

    /** 一次任务持有的准入名额，不能重复释放。 */
    public static final class Permit implements AutoCloseable {

        /** 所属准入器的在途计数。 */
        private final AtomicInteger active;

        /** 是否已经释放。 */
        private final AtomicBoolean released = new AtomicBoolean();

        /** @param active 所属准入器的在途计数 */
        private Permit(AtomicInteger active) {
            this.active = active;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                active.decrementAndGet();
            }
        }
    }
}
