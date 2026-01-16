package com.chua.socket.support.handler;

import com.chua.socket.support.SocketListener;
import com.chua.socket.support.annotations.OnEvent;
import com.chua.socket.support.session.SocketSession;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件监听器处理器
 * 扫描 @OnEvent 注解并分发事件到对应的处理方法
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-27
 */
@Slf4j
public class EventListenerProcessor {

    /**
     * 事件处理器映射
     * key: 事件名称, value: 处理器列表
     */
    private final Map<String, List<EventHandler>> eventHandlers = new ConcurrentHashMap<>();

    /**
     * 监听器列表
     */
    private final List<SocketListener> listeners;

    public EventListenerProcessor(List<SocketListener> listeners) {
        this.listeners = listeners != null ? listeners : List.of();
        scanListeners();
    }

    /**
     * 扫描所有监听器，注册 @OnEvent 方法
     */
    private void scanListeners() {
        for (SocketListener listener : listeners) {
            scanListener(listener);
        }
        log.info("[EventProcessor] 扫描完成，共注册 {} 个事件", eventHandlers.size());
    }

    /**
     * 扫描单个监听器
     *
     * @param listener 监听器
     */
    private void scanListener(SocketListener listener) {
        Class<?> clazz = listener.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            OnEvent onEvent = method.getAnnotation(OnEvent.class);
            if (onEvent != null) {
                String eventName = onEvent.value();
                method.setAccessible(true);
                
                EventHandler handler = new EventHandler(listener, method);
                eventHandlers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(handler);
                
                log.debug("[EventProcessor] 注册事件处理器: event={}, method={}.{}",
                        eventName, clazz.getSimpleName(), method.getName());
            }
        }
    }

    /**
     * 触发事件
     *
     * @param eventName 事件名称
     * @param session   会话
     * @param data      数据
     */
    public void fireEvent(String eventName, SocketSession session, Object data) {
        List<EventHandler> handlers = eventHandlers.get(eventName);
        if (handlers == null || handlers.isEmpty()) {
            log.debug("[EventProcessor] 未找到事件处理器: event={}", eventName);
            return;
        }

        for (EventHandler handler : handlers) {
            try {
                handler.invoke(session, data);
            } catch (Exception e) {
                log.error("[EventProcessor] 事件处理失败: event={}, handler={}",
                        eventName, handler.getMethodName(), e);
            }
        }
    }

    /**
     * 检查是否有指定事件的处理器
     *
     * @param eventName 事件名称
     * @return 是否存在处理器
     */
    public boolean hasHandler(String eventName) {
        List<EventHandler> handlers = eventHandlers.get(eventName);
        return handlers != null && !handlers.isEmpty();
    }

    /**
     * 获取所有已注册的事件名称
     *
     * @return 事件名称集合
     */
    public java.util.Set<String> getRegisteredEvents() {
        return eventHandlers.keySet();
    }

    /**
     * 事件处理器封装
     */
    private static class EventHandler {
        private final SocketListener listener;
        private final Method method;

        public EventHandler(SocketListener listener, Method method) {
            this.listener = listener;
            this.method = method;
        }

        public void invoke(SocketSession session, Object data) throws Exception {
            Class<?>[] paramTypes = method.getParameterTypes();
            
            switch (paramTypes.length) {
                case 0 -> method.invoke(listener);
                case 1 -> {
                    if (SocketSession.class.isAssignableFrom(paramTypes[0])) {
                        method.invoke(listener, session);
                    } else {
                        method.invoke(listener, data);
                    }
                }
                case 2 -> {
                    if (SocketSession.class.isAssignableFrom(paramTypes[0])) {
                        method.invoke(listener, session, data);
                    } else {
                        method.invoke(listener, data, session);
                    }
                }
                default -> log.warn("[EventProcessor] 不支持超过2个参数的事件处理方法: {}", getMethodName());
            }
        }

        public String getMethodName() {
            return listener.getClass().getSimpleName() + "." + method.getName();
        }
    }
}
