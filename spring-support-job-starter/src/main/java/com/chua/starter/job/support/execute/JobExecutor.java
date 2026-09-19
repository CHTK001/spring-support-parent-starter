package com.chua.starter.job.support.execute;

import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.log.JobLogHelper;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 执行器 / 单个 角色任务执行入口。
 *
 * <p>消费 {@link JobSyncBus} 上的 {@link JobSyncTopics#TASK_COMMAND} 事件,
 * 提交到内置执行器, 处理器 成功执行后构造
 * {@link JobLogEvent} 通过 bus 上的 {@link JobSyncTopics#TASK_LOG} 投递。
 * 执行器可为平台 {@link ThreadPoolExecutor} 或虚拟线程执行器 (见 {@link #defaultExecutor()})。</p>
 *
 * <p>SCHEDULER 角色不会注入本类; SINGLE 角色既消费也执行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class JobExecutor {

    /**
     * 默认线程名前缀
     */
    public static final String DEFAULT_THREAD_NAME_PREFIX = "job-exec-";

    /**
     * 默认核心线程数
     */
    public static final int DEFAULT_CORE_SIZE = 8;

    /**
     * 默认最大线程数
     */
    public static final int DEFAULT_MAX_SIZE = 32;

    /**
     * 默认队列容量
     */
    public static final int DEFAULT_QUEUE_CAPACITY = 1000;

    /**
     * 任务处理器注册表
     */
    private final JobHandlerFactory factory;

    /**
     * 事件总线
     */
    private final JobSyncBus bus;

    /**
     * 当前节点标识
     */
    private final String nodeId;

    /**
     * 执行器 (可为平台线程池或虚拟线程执行器)
     */
    private final ExecutorService executor;

    /**
     * 已处理任务计数
     */
    private final AtomicLong processed = new AtomicLong(0L);

    /**
     * 已丢弃任务计数
     */
    private final AtomicLong dropped = new AtomicLong(0L);

    /**
     * 已执行 初始化 生命周期钩子的 处理器 名称集合 (惰性, 每 处理器 仅一次)
     */
    private final java.util.Set<String> initializedHandlers =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * CMD 监听器引用 (持有, 用于注销)
     */
    private final java.util.function.BiConsumer<String, Object> cmdListener = (topic, payload) -> onCommand(topic, payload);

    /**
     * 构造执行器, 使用默认线程池 (虚拟线程执行器)。
     *
     * @param factory 处理器注册表
     * @param bus     进程内总线
     * @param nodeId  当前节点标识
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId) {
        this(factory, bus, nodeId, defaultExecutor());
    }

    /**
     * 构造执行器 (由 {@link com.chua.starter.job.support.JobAutoConfiguration} 装配)。
     *
     * @param factory  处理器注册表
     * @param bus      进程内总线
     * @param nodeId   当前节点标识
     * @param executor 自定义执行器
     */
    public JobExecutor(JobHandlerFactory factory, JobSyncBus bus, String nodeId,
                       ExecutorService executor) {
        this.factory = factory;
        this.bus = bus;
        this.nodeId = nodeId;
        this.executor = executor;
        bus.subscribe(JobSyncTopics.TASK_COMMAND, cmdListener);
    }

    /**
     * 接收到 CMD 事件的回调。
     *
     * @param topic   topic 名 (固定为 任务_命令)
     * @param payload 业务负载 (任务名)
     * @param source 源
     */
    private void onCommand(String source, Object payload) {
        log.info("[job] 收到 cmd source={} payload={}", source, payload);
        if (!(payload instanceof String jobName)) {
            return;
        }
        JobHandler handler = factory.get(jobName);
        if (handler == null) {
            log.warn("[job] handler 未注册 job={}", jobName);
            bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                    JobLogEvent.skipped(jobName, "handler 未注册", nodeId));
            dropped.incrementAndGet();
            return;
        }
        try {
            executor.execute(() -> invoke(jobName, handler));
        } catch (RejectedExecutionException ex) {
            dropped.incrementAndGet();
            log.warn("[job] 线程池拒绝 job={} cause={}", jobName, ex.getMessage());
        }
    }

    /**
     * 实际调用 处理器, 抛异常也仅记日志, 不影响其他任务。
     *
     * @param jobName 任务名
     * @param handler 处理器
     */
    private void invoke(String jobName, JobHandler handler) {
        log.info("[job] invoke start job={} handler={}", jobName, handler.getClass().getSimpleName());
        long start = System.currentTimeMillis();
        try {
            lazyInit(handler);
            handler.execute();
            long cost = System.currentTimeMillis() - start;
            processed.incrementAndGet();
            String logContent = JobLogHelper.getAndClear();
            log.info("[job] invoke ok job={} cost={}ms", jobName, cost);
            bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                    JobLogEvent.success(jobName, cost, start, nodeId, logContent));
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            processed.incrementAndGet();
            String logContent = JobLogHelper.getAndClear();
            if (logContent == null) {
                logContent = "";
            }
            String exMsg = ex.getMessage();
            logContent += "Exception: " + (exMsg != null ? exMsg : ex.getClass().getSimpleName()) + "\n";
            log.error("[job] 执行失败 job={} cause={}", jobName, exMsg, ex);
            bus.publish(JobSyncTopics.TASK_LOG, nodeId,
                    JobLogEvent.failure(jobName, cost, ex, start, nodeId, logContent));
        }
    }

    /**
     * 惰性执行 处理器 的 初始化 生命周期钩子 (每个 处理器 仅执行一次)。
     *
     * @param handler 处理器
     * @throws Exception 初始化 钩子异常
     */
    private void lazyInit(JobHandler handler) throws Exception {
        if (!initializedHandlers.add(handler.name())) {
            return;
        }
        handler.init();
        log.info("[job] 生命周期钩子 init handler={}", handler.name());
    }

    /**
     * 关闭执行器, 释放线程池, 并回调所有已 初始化 处理器 的 销毁 生命周期钩子。
     */
    public void shutdown() {
        bus.unsubscribe(JobSyncTopics.TASK_COMMAND, cmdListener);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        for (String name : initializedHandlers) {
            JobHandler handler = factory.get(name);
            if (handler == null) {
                continue;
            }
            try {
                handler.destroy();
                log.info("[job] 生命周期钩子 destroy handler={}", name);
            } catch (Throwable ex) {
                log.warn("[job] handler destroy 异常 name={} cause={}", name, ex.getMessage());
            }
        }
    }

    /**
     * 已处理任务计数。
     *
     * @return 累计数量
     */
    public long processedCount() {
        return processed.get();
    }

    /**
     * 已丢弃任务计数。
     *
     * @return 累计数量
     */
    public long droppedCount() {
        return dropped.get();
    }

    /**
     * 默认线程池工厂。
     *
     * @return ThreadFactory
     */
    public static ThreadFactory namedThreadFactory() {
        return new ThreadFactory() {
            /** 计数器 */
            private final AtomicLong counter = new AtomicLong(0L);

            @Override
            /** 新thread */
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, DEFAULT_THREAD_NAME_PREFIX + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
    }

    /**
     * 默认执行器: 虚拟线程执行器 (每任务一个虚拟线程, 阻塞型任务吞吐优势显著)。
     *
     * @return ExecutorService
     */
    public static ExecutorService defaultExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 平台线程池执行器 (核心/最大/队列 + abortpolicy), 供 {@code pool.virtual=false} 时使用。
     *
     * <p>注意: {@link java.util.concurrent.LinkedBlockingQueue} 只有队列填满后才扩线程至 max,
     * 默认 队列=1000 下线程数恒等于 核心。</p>
     *
     * @return ThreadPoolExecutor
     */
    public static ThreadPoolExecutor platformExecutor() {
        return new ThreadPoolExecutor(
                DEFAULT_CORE_SIZE,
                DEFAULT_MAX_SIZE,
                60L,
                TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                namedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
