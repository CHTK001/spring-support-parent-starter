package com.chua.starter.task.support.sse;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.json.Json;
import com.chua.common.support.log.Log;
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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.task.support.task.Task.SUBSCRIBE;

/**
 * sse
 *
 * @author CH
 */
@Service
public class SseEmitterService implements DisposableBean, InitializingBean {
    private static final Map<String, Sse> sseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorUpdateService = ThreadUtils.newScheduledThreadPoolExecutor("update-heart");
    @Resource
    private SseEventBus sseEventBus;
    private static final Log log = Log.getLogger(SseEmitter.class);

    /**
     * 通知
     *
     * @param taskTid 任务
     * @param exact   当前处理位置
     */
    public void emit(String taskTid, int exact) {
        this.sseEventBus.handleEvent(SseEvent.builder()
                .id(IdUtils.uuid())
                .clientIds(Collections.singleton(SUBSCRIBE))
                .data(Json.toJson(ImmutableBuilder.builderOfStringMap()
                        .put("count", exact)
                        .put("taskTid", taskTid)
                        .newHashMap()))
                .build());
    }

    /**
     * 通知
     *
     * @param taskTid 任务
     */
    public void unSubscribe(String taskTid) {
        this.sseEventBus.unregisterClient(taskTid);
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.shutdownNow(scheduledExecutorUpdateService);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        long toMillis = TimeUnit.SECONDS.toMillis(30);
        scheduledExecutorUpdateService.scheduleAtFixedRate(() -> {
            for (Sse sse : sseCache.values()) {
                if (System.currentTimeMillis() - sse.getCreateTime() < toMillis) {
                    unSubscribe(sse.taskTid);
                }
            }
        }, 0, 5, TimeUnit.MINUTES);
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
