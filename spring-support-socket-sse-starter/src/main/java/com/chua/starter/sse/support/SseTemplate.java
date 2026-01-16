package com.chua.starter.sse.support;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.IdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;


/**
 * sse
 *
 * @author CH
 */
@EnableSseEventBus
public class SseTemplate {
    @Autowired
    private SseEventBus sseEventBus;

    @Autowired
    private DefaultSseEventBusConfigurer sseEventBusConfigurer;

    /**
     * 通知
     *
     * @param sseMessage 消息
     * @param clientIds  通知的客户端
     */
    public void emit(SseMessage sseMessage, String... clientIds) {
        emit(sseMessage, null, clientIds);
    }

    /**
     * 通知
     *
     * @param sseMessage 消息
     * @param retry      重试时间
     * @param clientIds  通知的客户端
     */
    public void emit(SseMessage sseMessage, Duration retry, String... clientIds) {
        SseEvent.Builder builder = SseEvent.builder()
                .id(IdUtils.uuid())
                .event(sseMessage.getEvent())
                .clientIds(Arrays.asList(clientIds))
                .data(Json.toJson(sseMessage));
        if (null != retry) {
            builder.retry(retry);
        }
        this.sseEventBus.handleEvent(builder.build());

    }
    /**
     * 通知
     *
     * @param id        消息id
     * @param data      消息
     * @param retry     重试时间
     * @param clientIds 通知的客户端
     */
    public void emit(String id, String data, String event, Duration retry, String... clientIds) {
        SseEvent.Builder builder = SseEvent.builder()
                .id(id)
                .event(event)
                .clientIds(Arrays.asList(clientIds))
                .data(data);
        if (null != retry) {
            builder.retry(retry);
        }
        this.sseEventBus.handleEvent(builder.build());

    }
    /**
     * 创建sse
     * 创建任务
     *
     * @param emitter  emitter
     * @return 结果
     */
    public SseEmitter createSseEmitter(Emitter emitter) {
        SseEmitter sseEmitter = sseEventBus.createSseEmitter(emitter.getClientId(), emitter.getEvent().toArray(new String[0]));
        emitter.setSseEmitter(sseEmitter);
        sseEventBusConfigurer.register(emitter);
        return sseEmitter;
    }


    /**
     * 获取客户端
     *
     * @param event 事件
     * @return 客户端
     */
    public List<Emitter> getEmitter(String event) {
        return sseEventBusConfigurer.getEmitter(event);
    }

    /**
     * 关闭Emitter
     *
     * @param clientIds 客户端id
     */
    public void closeEmitter(List<String> clientIds) {
        sseEventBusConfigurer.closeEmitter(clientIds);
        for (String clientId : clientIds) {
            sseEventBus.unregisterClient(clientId);
        }
    }
}
