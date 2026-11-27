package com.chua.starter.logger.store;

import com.chua.starter.logger.event.LoggerEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 日志事件总线：服务端收到事件后经总线分发给存储与 SSE 订阅者。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LoggerEventBus {

    private final List<Consumer<LoggerEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 已告警过的订阅者（按实现类去重）：订阅者持续失败只告警一次，避免随事件流刷屏
     */
    private final Set<Class<?>> warnedListeners = ConcurrentHashMap.newKeySet();

    /**
     * 订阅事件；返回取消订阅的 Runnable
     *
     * @param listener 订阅者
     * @return 取消订阅操作
     */
    public Runnable subscribe(Consumer<LoggerEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * 发布一批事件
     *
     * @param events 事件列表
     */
    public void publish(List<LoggerEvent> events) {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }
        for (LoggerEvent event : events) {
            for (Consumer<LoggerEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    // 单个订阅者失败不影响其它订阅者；但必须留痕，否则订阅方会静默失联
                    if (warnedListeners.add(listener.getClass())) {
                        log.warn("[logger] 事件订阅者 {} 处理失败（该订阅者仅告警一次）: {}",
                                listener.getClass().getName(), e.getMessage());
                    }
                }
            }
        }
    }
}
