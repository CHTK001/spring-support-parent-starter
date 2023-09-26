package com.chua.starter.sse.support;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.ThreadUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * sse
 *
 * @author CH
 */
@EnableSseEventBus
public class SseTemplate implements DisposableBean, InitializingBean {
    private static final Map<String, List<Emitter>> sseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorUpdateService = ThreadUtils.newScheduledThreadPoolExecutor("update-heart");
    @Resource
    private SseEventBus sseEventBus;

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
        updateHeart(clientIds);
        this.sseEventBus.handleEvent(builder.build());

    }

    private void updateHeart(String[] clientIds) {
        for (String clientId : clientIds) {
            List<Emitter> sse = sseCache.get(clientId);
            if (null == sse) {
                continue;
            }

            for (Emitter sse1 : sse) {
                sse1.setCreateTime(System.nanoTime());
            }
        }
    }

    /**
     * 通知
     *
     * @param clientIds 客户端
     */
    public void unSubscribe(String... clientIds) {
        for (String clientId : clientIds) {
            this.sseEventBus.unregisterClient(clientId);
            List<Emitter> emitters = sseCache.get(clientId);
            for (Emitter emitter : emitters) {
                IoUtils.closeQuietly(emitter.getEntity());
            }

            sseCache.remove(clientId);
        }
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.shutdownNow(scheduledExecutorUpdateService);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        long toMillis = TimeUnit.SECONDS.toNanos(30);
        scheduledExecutorUpdateService.scheduleAtFixedRate(() -> {
            for (List<Emitter> sse : sseCache.values()) {
                for (Emitter sse1 : sse) {
                    if (System.currentTimeMillis() - sse1.getCreateTime() < toMillis) {
                        unSubscribe(sse1.getClientId());
                    }
                }
            }
        }, 0, 3, TimeUnit.MINUTES);
    }

    /**
     * 创建sse发射器
     * 创建任务
     *
     * @param emitter 发射器
     * @return 结果
     */
    public SseEmitter createSseEmitter(Emitter emitter) {
        SseEmitter sseEmitter = sseEventBus.createSseEmitter(emitter.getClientId(), emitter.getEvent().toArray(new String[0]));
        emitter.setSseEmitter(sseEmitter);
        sseCache.computeIfAbsent(emitter.getClientId(), it -> new LinkedList<>()).add(emitter);
        return sseEmitter;
    }


    /**
     * 获取（ss）发射器
     *
     * @param clientId 客户端id
     * @return {@link SseEmitter}
     */
    public SseEmitter getSseEmitter(String clientId) {
        List<Emitter> emitters = sseCache.get(clientId);
        if (CollectionUtils.size(emitters) == 1) {
            return emitters.get(0).getSseEmitter();
        }

        return null;
    }
}
