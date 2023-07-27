package com.chua.starter.sse.support;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.ThreadUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
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
@Service
public class SseTemplate implements DisposableBean, InitializingBean {
    private static final Map<String, List<Sse>> sseCache = new ConcurrentHashMap<>();
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
            List<Sse> sse = sseCache.get(clientId);
            if (null == sse) {
                continue;
            }

            for (Sse sse1 : sse) {
                sse1.setCreateTime(System.currentTimeMillis());
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
            sseCache.remove(clientId);
        }
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.shutdownNow(scheduledExecutorUpdateService);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        long toMillis = TimeUnit.SECONDS.toMillis(30);
        scheduledExecutorUpdateService.scheduleAtFixedRate(() -> {
            for (List<Sse> sse : sseCache.values()) {
                for (Sse sse1 : sse) {
                    if (System.currentTimeMillis() - sse1.getCreateTime() < toMillis) {
                        unSubscribe(sse1.getTaskTid());
                    }
                }
            }
        }, 0, 10, TimeUnit.MINUTES);
    }

    /**
     * 创建任务
     * @param clientId 客户端ID
     * @param event     事件
     * @return 结果
     */
    public SseEmitter createSseEmitter(String clientId, String... event) {
        SseEmitter sseEmitter = sseEventBus.createSseEmitter(clientId, event);
        sseCache.computeIfAbsent(clientId, it -> new LinkedList<>()).add(new Sse(clientId, sseEmitter, System.currentTimeMillis()));
        return sseEmitter;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static final class Sse {

        private String taskTid;

        private SseEmitter sseEmitter;
        private long createTime;
    }
}
