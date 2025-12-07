package com.chua.starter.common.support.log;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 支持MDC的线程池包装器
 * <p>
 * 在任务执行时自动传递MDC上下文。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
public class ThreadPoolExecutorMdcWrapper extends ThreadPoolExecutor {

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                         TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                         TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                         ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                         TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                         RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                         TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                         ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void execute(@NonNull Runnable command) {
        super.execute(ThreadMdcUtil.wrap(command, MDC.getCopyOfContextMap()));
    }

    @Override
    @NonNull
    public Future<?> submit(@NonNull Runnable task) {
        return super.submit(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    @NonNull
    public <T> Future<T> submit(@NonNull Callable<T> task) {
        return super.submit(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    @NonNull
    public <T> Future<T> submit(@NonNull Runnable task, T result) {
        return super.submit(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()), result);
    }
}
