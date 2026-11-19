package com.chua.starter.common.support.log;

import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * SLF4J MDC 串联工具（跨线程 / 线程池传递链路上下文）。
 * <p>
 * 仅依赖 {@code org.slf4j.MDC} 标准接口，与具体日志实现（logback / log4j2 / 其它 SLF4J Provider）解耦；
 * 纯装饰器实现，无需 javaagent。
 * </p>
 * <p>
 * 语义：在<strong>提交任务</strong>时对当前线程的 MDC 做快照，任务执行前恢复、执行结束后还原现场，
 * 既把提交方的 traceId 等上下文带给异步线程，也不污染线程池中复用线程的下一次任务。
 * </p>
 * <h3>用法</h3>
 * <pre>
 * // 1. 包单个任务（必须在提交线程上调用 wrap）
 * executor.submit(MdcWrap.wrap(runnable));
 * executor.submit(MdcWrap.wrap(callable));
 *
 * // 2. 包整个执行器（一劳永逸，提交进来的任务自动串联 MDC）
 * ExecutorService executor = MdcWrap.decorate(Executors.newFixedThreadPool(8));
 * CompletableFuture.supplyAsync(supplier, MdcWrap.decorate(otherExecutor));
 * </pre>
 * <p>
 * Spring 托管的线程池（{@code ThreadPoolTaskExecutor}）请优先使用 {@link MdcTaskDecorator}；
 * 本工具面向手写线程池、CompletableFuture、虚拟线程池等 JDK 执行器场景。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class MdcWrap {

    /**
     * 构造函数
     */
    private MdcWrap() {
    }

    /**
     * 包装 Runnable：提交时快照当前 MDC，执行前恢复、结束后还原现场。
     * @param task 原始任务
     * @return 带 MDC 串联能力的任务
     */
    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task");
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            try {
                restore(snapshot);
                task.run();
            } finally {
                restore(backup);
            }
        };
    }

    /**
     * 包装 Callable：提交时快照当前 MDC，执行前恢复、结束后还原现场。
     * @param task 原始任务
     * @param <V> 返回值类型
     * @return 带 MDC 串联能力的任务
     */
    public static <V> Callable<V> wrap(Callable<V> task) {
        Objects.requireNonNull(task, "task");
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            try {
                restore(snapshot);
                return task.call();
            } finally {
                restore(backup);
            }
        };
    }

    /**
     * 包装 Supplier：提交时快照当前 MDC，执行前恢复、结束后还原现场。
     * <p>用于 {@code CompletableFuture.supplyAsync(supplier)} 等返回值任务场景；
     * 与 {@link #wrap(Callable)} 分开命名，避免 lambda 目标类型推断时的重载歧义。</p>
     * @param task 原始任务
     * @param <V> 返回值类型
     * @return 带 MDC 串联能力的任务
     */
    public static <V> Supplier<V> wrapSupplier(Supplier<V> task) {
        Objects.requireNonNull(task, "task");
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            try {
                restore(snapshot);
                return task.get();
            } finally {
                restore(backup);
            }
        };
    }

    /**
     * 装饰整个执行器：此后提交到该执行器的所有任务自动串联提交方 MDC。
     * <p>内部仅改写 {@code execute}，submit / invokeAll / invokeAny 均经由此入口，统一生效。</p>
     * @param delegate 被装饰的执行器
     * @return 带 MDC 串联能力的执行器
     */
    public static ExecutorService decorate(ExecutorService delegate) {
        return new MdcExecutorService(delegate);
    }

    /**
     * 恢复 MDC 现场：空快照表示清理，避免污染复用线程。
     * @param context MDC 快照
     */
    private static void restore(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    /**
     * MDC 串联执行器：把提交进来的任务统一用 {@link MdcWrap#wrap(Runnable)} 包装。
     */
    private static final class MdcExecutorService extends AbstractExecutorService {

        /**
         * 被装饰的执行器
         */
        private final ExecutorService delegate;

        /**
         * MDC 串联执行器。
         * @param delegate 被装饰的执行器
         */
        private MdcExecutorService(ExecutorService delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(wrap(command));
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
