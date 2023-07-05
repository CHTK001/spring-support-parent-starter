package com.chua.starter.task.support.sse;

import com.chua.common.support.utils.ThreadUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * sse
 * @author CH
 */
@Service
public class SseEmitterService implements DisposableBean, InitializingBean {
    private static final Map<String, Sse> sseCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorUpdateService = ThreadUtils.newScheduledThreadPoolExecutor("update-heart");


    /**
     * sse
     * @param taskTid 任务ID
     */
    public void emit(String taskTid) {
        SseEmitter sseEmitter = new SseEmitter(3600_000L);
        sseCache.put(taskTid, new Sse(taskTid, sseEmitter, System.currentTimeMillis()));
        sseEmitter.onTimeout(() -> sseCache.remove(taskTid));
        sseEmitter.onCompletion(() -> System.out.println("完成！！！"));
    }

    /**
     * 通知
     * @param taskTid 任务
     * @param exact 当前处理位置
     */
    public void emit(String taskTid, int exact) {
        Sse sse = sseCache.get(taskTid);
        if(null == sse) {
            return;
        }

        try {
            sse.sseEmitter.send(exact);
        } catch (IOException ignored) {
        }
    }
    /**
     * 通知
     * @param taskTid 任务
     */
    public void unEmit(String taskTid) {
        Sse sse = sseCache.get(taskTid);
        if(null == sse) {
            return;
        }

        sse.sseEmitter.complete();
        sseCache.remove(taskTid);
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
                if(System.currentTimeMillis() - sse.getCreateTime() < toMillis ) {
                    unEmit(sse.taskTid);
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
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
