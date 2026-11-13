// [P3C C1 豁免] C1: 内部创建虚拟线程池，见第 43 行。
package com.chua.starter.device.support.callback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 设备生命周期回调发布器。
 *
 * <p>容器内全部 {@link DeviceLifecycleCallback} Bean 都是投递目标：每个实现丢进一个
 * 虚拟线程并发执行，发布器等待全部结果（单批总超时 10 秒），超时也记为失败结果。
 * 之所以要等结果：只有拿到每个下游的执行结果，才能在失败时明确发出通知、
 * 让下游回滚它自己在别的服务器上的操作；纯 fire-and-forget 做不到这一点。</p>
 *
 * <p>回调失败不会回滚本地设备入库（设备台账是主数据，回调是旁路），
 * 只记录失败并把 FAILED 事件通知出去，由下游自行补偿。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Component
public class DeviceLifecyclePublisher implements DisposableBean {

    /** 单批回调整体等待上限（秒） */
    private static final long TIMEOUT_SECONDS = 10L;

    /** 回调实现提供者：没有实现时 orderedStream() 返回空流，不会因缺 Bean 启动失败 */
    private final ObjectProvider<DeviceLifecycleCallback> providers;

    /** 每次回调一个虚拟线程，避免下游操作别的服务器时阻塞业务线程 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 构造发布器。
     *
     * @param providers 回调实现提供者
     */
    public DeviceLifecyclePublisher(ObjectProvider<DeviceLifecycleCallback> providers) {
        this.providers = providers;
    }

    /**
     * 列出当前已注册的回调名称（纯后端排查用）。
     *
     * @return 回调名称列表
     */
    public List<String> callbackNames() {
        return providers.orderedStream().map(DeviceLifecycleCallback::name).toList();
    }

    /**
     * 把事件投递给全部关心它的回调，并等待执行结果。
     *
     * @param event 生命周期事件
     * @return 每个回调的执行结果（顺序与 Bean 顺序一致）
     */
    public List<DeviceCallbackResult> publish(DeviceLifecycleEvent event) {
        List<DeviceLifecycleCallback> callbacks = providers.orderedStream()
                .filter(item -> item.supports(event))
                .toList();
        if (callbacks.isEmpty()) {
            return List.of();
        }
        List<Callable<DeviceCallbackResult>> tasks = new ArrayList<>(callbacks.size());
        for (DeviceLifecycleCallback callback : callbacks) {
            tasks.add(() -> invoke(callback, event));
        }
        try {
            List<Future<DeviceCallbackResult>> futures =
                    executor.invokeAll(tasks, TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<DeviceCallbackResult> results = new ArrayList<>(futures.size());
            for (int i = 0; i < futures.size(); i++) {
                results.add(collect(callbacks.get(i), futures.get(i)));
            }
            return results;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("设备生命周期回调被中断: type={}, phase={}", event.eventType(), event.phase());
            return callbacks.stream()
                    .map(item -> new DeviceCallbackResult(item.name(), false, "回调被中断", 0L))
                    .toList();
        }
    }

    /**
     * 执行单个回调，异常收敛为失败结果，不向发布器外抛。
     *
     * @param callback 回调实现
     * @param event    事件
     * @return 执行结果
     */
    private static DeviceCallbackResult invoke(DeviceLifecycleCallback callback, DeviceLifecycleEvent event) {
        long start = System.currentTimeMillis();
        try {
            callback.onEvent(event);
            return new DeviceCallbackResult(callback.name(), true, "OK", System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("设备生命周期回调失败: callback={}, type={}, phase={}, reason={}",
                    callback.name(), event.eventType(), event.phase(), ex.getMessage());
            return new DeviceCallbackResult(callback.name(), false,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * 收集 Future 结果；取消（超时）与执行异常都归一为失败结果。
     *
     * @param callback 回调实现
     * @param future   任务 Future
     * @return 执行结果
     */
    private static DeviceCallbackResult collect(DeviceLifecycleCallback callback,
                                                Future<DeviceCallbackResult> future) {
        if (future.isCancelled()) {
            return new DeviceCallbackResult(callback.name(), false,
                    // [P3C A20 豁免]
                    "回调超时（" + TIMEOUT_SECONDS + " 秒）", TIMEOUT_SECONDS * 1000L);
        }
        try {
            return future.get();
        } catch (ExecutionException ex) {
            return new DeviceCallbackResult(callback.name(), false,
                    String.valueOf(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()), 0L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DeviceCallbackResult(callback.name(), false, "结果等待被中断", 0L);
        }
    }

    /**
     * 应用关闭时释放虚拟线程池。
     */
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
