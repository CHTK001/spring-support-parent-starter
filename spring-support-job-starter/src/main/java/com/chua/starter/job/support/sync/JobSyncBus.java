package com.chua.starter.job.support.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 进程内事件总线。
 *
 * <p>本模块所有组件通过本总线解耦:</p>
 * <ul>
 *   <li>SCHEDULER 角色: {@link #publish} → 通过 {@code SchedulerSyncBinder} 转推到所有 Executor</li>
 *   <li>EXECUTOR 角色: 远端事件经 {@link #publish} 派发 → {@code JobExecutor} 真正调用 handler</li>
 *   <li>SINGLE 角色: LeaderLoop 直接 publish, Executor 直接消费, 不走网络</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class JobSyncBus {

    /**
     * 按 topic 的本地监听器, key 为 topic
     */
    private final Map<String, CopyOnWriteArrayList<BiConsumer<String, Object>>> listeners = new ConcurrentHashMap<>();

    /**
     * 投递事件到指定 topic。
     *
     * @param topic   topic 名
     * @param source  来源标识 (本地为 local, 远端为节点 id)
     * @param payload 业务负载
     */
    public void publish(String topic, String source, Object payload) {
        CopyOnWriteArrayList<BiConsumer<String, Object>> list = listeners.get(topic);
        if (list == null) {
            return;
        }
        for (BiConsumer<String, Object> listener : list) {
            try {
                listener.accept(source, payload);
            } catch (Throwable ex) {
                log.error("[job] 总线处理异常 topic={} cause={}", topic, ex.getMessage(), ex);
            }
        }
    }

    /**
     * 按 topic 注册监听器。
     *
     * @param topic    topic 名
     * @param listener 监听器
     */
    public void subscribe(String topic, BiConsumer<String, Object> listener) {
        listeners.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 按 topic 注销监听器。
     *
     * @param topic    topic 名
     * @param listener 监听器
     */
    public void unsubscribe(String topic, BiConsumer<String, Object> listener) {
        CopyOnWriteArrayList<BiConsumer<String, Object>> list = listeners.get(topic);
        if (list != null) {
            list.remove(listener);
        }
    }
}
