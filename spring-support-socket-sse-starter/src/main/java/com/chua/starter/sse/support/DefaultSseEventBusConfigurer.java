package com.chua.starter.sse.support;

import ch.rasc.sse.eventbus.ClientEvent;
import ch.rasc.sse.eventbus.SseEventBusListener;
import ch.rasc.sse.eventbus.config.SseEventBusConfigurer;
import com.chua.common.support.utils.IoUtils;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认SSE事件总线配置
 *
 * @author CH
 * @since 2023/09/26
 */
public class DefaultSseEventBusConfigurer implements SseEventBusConfigurer {
    private static final Map<String, List<Emitter>> SSE_CACHE = new ConcurrentHashMap<>();

    @Override
    public Duration clientExpiration() {
        return Duration.ofMinutes(1);
    }

    @Override
    public SseEventBusListener listener() {
        return new SseEventBusListener() {
            @Override
            public void afterEventQueued(ClientEvent clientEvent, boolean firstAttempt) {
                SseEventBusListener.super.afterEventQueued(clientEvent, firstAttempt);
            }

            @Override
            public void afterEventSent(ClientEvent clientEvent, Exception exception) {
                SseEventBusListener.super.afterEventSent(clientEvent, exception);
            }

            @Override
            public void afterClientsUnregistered(Set<String> clientIds) {
                for (String clientId : clientIds) {
                    List<Emitter> emitters = SSE_CACHE.get(clientId);
                    for (Emitter emitter : emitters) {
                        IoUtils.closeQuietly(emitter.getEntity());
                    }
                }
                SseEventBusListener.super.afterClientsUnregistered(clientIds);
            }
        };
    }

    /**
     * 注册
     *
     * @param emitter emitter
     */
    public void register(Emitter emitter) {
        SSE_CACHE.computeIfAbsent(emitter.getClientId(), it -> new LinkedList<>()).add(emitter);
    }
    /**
     * 获取Emitter
     *
     * @param event 事件
     * @return 结果
     */
    public List<Emitter> getEmitter(String event) {
        List<Emitter> result = new LinkedList<>();
        for (List<Emitter> emitterList : SSE_CACHE.values()) {

            for (Emitter emitter : emitterList) {
                if (emitter.getEvent().contains(event)) {
                    result.add(emitter);
                }
            }
        }
        return result;
    }

    /**
     * 关闭Emitter
     *
     * @param clientIds 客户端id
     */
    public void closeEmitter(List<String> clientIds) {
        for (String clientId : clientIds) {
            List<Emitter> emitters = SSE_CACHE.get(clientId);
            for (Emitter emitter : emitters) {
                IoUtils.closeQuietly(emitter.getEntity());
                HttpServletResponse response = emitter.getResponse();
                if(null != response) {
                    try {
                        IoUtils.closeQuietly(response.getOutputStream());
                    } catch (IOException ignored) {
                    }
                }
            }

            SSE_CACHE.remove(clientId);
        }
    }
}
