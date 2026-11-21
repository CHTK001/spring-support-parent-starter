package com.chua.starter.logger.store;

import com.chua.starter.logger.event.LoggerEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 日志事件总线：服务端收到事件后经总线分发给存储与 SSE 订阅者。
 *
 * @author CH
 * @since 4.0.0.51
 */
public class LoggerEventBus {

    private final List<Consumer<LoggerEvent>> listeners = new CopyOnWriteArrayList<>();

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
        if (events == null || events.isEmpty()) {
            return;
        }
        for (LoggerEvent event : events) {
            for (Consumer<LoggerEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    // 单个订阅者失败不影响其它订阅者
                }
            }
        }
    }
}
