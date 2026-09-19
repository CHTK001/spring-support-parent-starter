package com.chua.starter.sse.support;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 事件总线配置
 * 管理所有 SSE 连接和事件分发
 *
 * @since 2023/09/26
 * @author CH
 */
@Slf4j
public class DefaultSseEventBusConfigurer {

    /**
     * 客户端连接缓存
     * Key: 客户端标识, 值: Emitter列表
     */
    private static final Map<String, List<Emitter>> SSE_CACHE = new ConcurrentHashMap<>();

    /**
     * 注册 Emitter
     *
     * @param emitter 待注册的 Emitter
     */
    public void register(Emitter emitter) {
        SSE_CACHE.computeIfAbsent(emitter.getClientId(), it -> new LinkedList<>()).add(emitter);
        log.debug("[SSE][连接管理] 注册客户端: clientId={}", emitter.getClientId());
            }

    /**
     * 注销 Emitter
     *
     * @param clientId 客户端标识
     */
    public void unregister(String clientId) {
        var emitters = SSE_CACHE.remove(clientId);
        if (emitters != null) {
            for (var emitter : emitters) {
                try {
                    if (emitter.getSseEmitter() != null) {
                        emitter.getSseEmitter().complete();
                    }
                } catch (Exception ignored) {
                }
            }
            log.debug("[SSE][连接管理] 注销客户端: clientId={}", clientId);
            }
    }

    /**
     * 根据客户端标识获取 Emitter
     *
     * @param clientId 客户端标识
     * @return 第一个匹配的 Emitter，不存在返回 空
     */
    public Emitter getEmitterByClientId(String clientId) {
        var emitters = SSE_CACHE.get(clientId);
        if (emitters != null && !emitters.isEmpty()) {
            return emitters.getFirst();
    }
        return null;
    }

    /**
     * 获取订阅指定事件的所有 Emitter
     *
     * @param event 事件名称
     * @return 匹配的 Emitter 列表
     */
    public List<Emitter> getEmitter(String event) {
        List<Emitter> result = new LinkedList<>();
        for (var emitterList : SSE_CACHE.values()) {
            for (var emitter : emitterList) {
                if (emitter.getEvent().contains(event)) {
                    result.add(emitter);
                }
            }
        }
        return result;
    }

    /**
     * 关闭指定客户端的连接
     *
     * @param clientIds 客户端标识列表
     */
    public void closeEmitter(List<String> clientIds) {
        for (var clientId : clientIds) {
            var emitters = SSE_CACHE.get(clientId);
            if (emitters == null) {
                continue;
            }
            for (var emitter : emitters) {
                try {
                    if (emitter.getSseEmitter() != null) {
                        emitter.getSseEmitter().complete();
                    }
                } catch (Exception ignored) {
                }
                var response = emitter.getResponse();
                if (response != null) {
                    try {
                        response.getOutputStream().close();
                    } catch (IOException ignored) {
                        // 忽略关闭异常
                    }
                }
            }
            SSE_CACHE.remove(clientId);
            log.debug("[SSE][连接管理] 关闭客户端连接: clientId={}", clientId);
        }
    }

    /**
     * 获取当前连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return SSE_CACHE.size();
    }
}
