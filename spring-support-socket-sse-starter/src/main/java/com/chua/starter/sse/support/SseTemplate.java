package com.chua.starter.sse.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * SSE 事件发送模板
 * 基于 Spring 内置的 SseEmitter 实现
 *
 * @author CH
 */
@Slf4j
public class SseTemplate {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final DefaultSseEventBusConfigurer sseEventBusConfigurer;

    public SseTemplate(DefaultSseEventBusConfigurer sseEventBusConfigurer) {
        this.sseEventBusConfigurer = sseEventBusConfigurer;
    }

    /**
     * 发送消息到指定客户端
     *
     * @param sseMessage 消息内容
     * @param clientIds  目标客户端ID列表
     */
    public void emit(SseMessage sseMessage, String... clientIds) {
        emit(sseMessage, null, clientIds);
    }

    /**
     * 发送消息到指定客户端（带重试时间）
     *
     * @param sseMessage 消息内容
     * @param retry      重试间隔
     * @param clientIds  目标客户端ID列表
     */
    public void emit(SseMessage sseMessage, Duration retry, String... clientIds) {
        try {
            var messageId = UUID.randomUUID().toString();
            var event = sseMessage.getEvent();
            var data = OBJECT_MAPPER.writeValueAsString(sseMessage);

            for (String clientId : clientIds) {
                var emitter = sseEventBusConfigurer.getEmitterByClientId(clientId);
                if (emitter != null && emitter.getSseEmitter() != null) {
                    sendEvent(emitter.getSseEmitter(), messageId, event, data, retry);
                }
            }
        } catch (Exception e) {
            log.error("[SSE][消息发送] 发送SSE消息失败", e);
            throw new RuntimeException("发送SSE消息失败", e);
        }
    }

    /**
     * 发送消息到指定客户端
     *
     * @param id        消息ID
     * @param data      消息数据
     * @param event     事件名称
     * @param retry     重试间隔
     * @param clientIds 目标客户端ID列表
     */
    public void emit(String id, String data, String event, Duration retry, String... clientIds) {
        for (String clientId : clientIds) {
            var emitter = sseEventBusConfigurer.getEmitterByClientId(clientId);
            if (emitter != null && emitter.getSseEmitter() != null) {
                sendEvent(emitter.getSseEmitter(), id, event, data, retry);
            }
        }
    }

    /**
     * 创建 SSE 连接
     *
     * @param emitter 发射器配置
     * @return SseEmitter 实例
     */
    public SseEmitter createSseEmitter(Emitter emitter) {
        var sseEmitter = new SseEmitter(0L);
        emitter.setSseEmitter(sseEmitter);
        sseEventBusConfigurer.register(emitter);

        sseEmitter.onCompletion(() -> sseEventBusConfigurer.unregister(emitter.getClientId()));
        sseEmitter.onTimeout(() -> sseEventBusConfigurer.unregister(emitter.getClientId()));
        sseEmitter.onError(e -> sseEventBusConfigurer.unregister(emitter.getClientId()));

        return sseEmitter;
    }

    /**
     * 获取指定事件的所有 Emitter
     *
     * @param event 事件名称
     * @return Emitter 列表
     */
    public List<Emitter> getEmitter(String event) {
        return sseEventBusConfigurer.getEmitter(event);
    }

    /**
     * 关闭指定客户端的连接
     *
     * @param clientIds 客户端ID列表
     */
    public void closeEmitter(List<String> clientIds) {
        sseEventBusConfigurer.closeEmitter(clientIds);
    }

    /**
     * 发送 SSE 事件
     *
     * @param sseEmitter SSE发射器
     * @param id         消息ID
     * @param event      事件名称
     * @param data       消息数据
     * @param retry      重试间隔
     */
    private void sendEvent(SseEmitter sseEmitter, String id, String event, String data, Duration retry) {
        try {
            var builder = SseEmitter.event()
                    .id(id)
                    .name(event)
                    .data(data);
            if (retry != null) {
                builder.reconnectTime(retry.toMillis());
            }
            sseEmitter.send(builder);
        } catch (IOException e) {
            log.error("[SSE][消息发送] 发送事件失败: event={}", event, e);
        }
    }
}
