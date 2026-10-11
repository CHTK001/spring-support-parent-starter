package com.chua.starter.datasourcehub.support.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 数据源指标的 SSE 订阅者注册表。
 *
 * <p>结构与 nginx 那条推送链路一致（含「必须自己序列化成 byte[]」那条约束：
 * 传对象会被项目的 {@code BinaryHttpMessageConverter} 认领并在调度线程上
 * 因为拿不到 {@code ThreadLocal} 请求而静默写出 0 字节，事件照常到达、
 * {@code e.data} 恒为空串；传字符串则会被 Jackson 再转一次，整个载荷被套上引号）。
 * 原因详见 {@code NginxMetricStreamRegistry#sendJson}。</p>
 *
 * <p><strong>与 nginx 那条的关键差异：订阅即开启采集。</strong>nginx 是定时把
 * 全部 nginx 数据源都探测一遍；这里只在存在订阅者时才探测对应数据源 ——
 * 数据源中心可能挂着上百条台账，没人看的那些没有理由产生探测流量。
 * 所以 {@link #watchedIds()} 是调度器的唯一输入，而不是台账的全集。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
public class DatasourceMetricStreamRegistry {

    /**
     * 事件名：一次探测结果 + 最近窗口。
     */
    public static final String EVENT_METRIC = "metric";

    /**
     * SSE 连接超时（毫秒）。设为 0 表示交给容器不超时，由心跳负责保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 订阅者：数据源 id → 该数据源的连接集合。
     *
     * <p>用 {@link CopyOnWriteArraySet}：广播是高频读，增删只在建连/断开时发生，
     * 读多写少且两个线程并发。</p>
     */
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    /**
     * 构造注入。
     *
     * @param objectMapper JSON 序列化器
     */
    @Autowired
    public DatasourceMetricStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅。
     *
     * <p>建连后立刻推一个 {@code ready} 事件：前端能在收到业务数据之前
     * 就把「已连接」点亮，否则只能靠超时猜链路是否建立成功。</p>
     *
     * @param datasourceId 数据源 id
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe(Long datasourceId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(datasourceId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        log.info("[datasource-metric] SSE 订阅建立 datasourceId={} 连接数={}", datasourceId, set.size());

        // 四个回调都指向同一个移除动作：连接无论以哪种方式结束都要摘掉
        Runnable remove = () -> remove(datasourceId, emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[datasource-metric] SSE 连接异常关闭 datasourceId={} reason={}",
                    datasourceId, e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[datasource-metric] SSE 连接超时 datasourceId={}", datasourceId);
            remove.run();
        });

        try {
            sendJson(emitter, "ready", Map.of("datasourceId", datasourceId));
        } catch (IOException e) {
            log.debug("[datasource-metric] SSE 初始帧发送失败 datasourceId={} reason={}",
                    datasourceId, e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 当前正在被观看的数据源 id 集合。
     *
     * <p>调度器只探测这些 id —— 这是「有人看才采」的那一半；
     * 「看过之后仍要能查历史」由 {@code ms_datasource_metric} 落库负责。</p>
     *
     * @return 订阅中的数据源 id
     */
    public Set<Long> watchedIds() {
        return Set.copyOf(subscribers.keySet());
    }

    /**
     * 向某数据源的所有订阅者广播一个事件。
     *
     * <p>单连接失败只摘掉该连接：多个运维同时开着数据源中心是常态，
     * 一个标签页关掉不能让别人的图停更。</p>
     *
     * @param datasourceId 数据源 id
     * @param event        事件名
     * @param payload      载荷
     * @return 成功送达的连接数
     */
    public int broadcast(Long datasourceId, String event, Object payload) {
        Set<SseEmitter> set = subscribers.get(datasourceId);
        if (set == null || set.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : set) {
            try {
                sendJson(emitter, event, payload);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                // IllegalStateException：连接已完成后再 send
                log.debug("[datasource-metric] SSE 推送失败，摘除连接 datasourceId={} reason={}",
                        datasourceId, e.getMessage());
                remove(datasourceId, emitter);
            }
        }
        return delivered;
    }

    /**
     * 把载荷序列化成 JSON 字节后作为一个事件帧发出。
     *
     * <p>必须传 {@code byte[]}：理由见类注释，详见
     * {@code NginxMetricStreamRegistry#sendJson}。</p>
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷
     * @throws IOException 序列化失败或连接已断
     */
    private void sendJson(SseEmitter emitter, String event, Object payload) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(payload);
        emitter.send(SseEmitter.event().name(event).data(json));
    }

    /**
     * 移除一条连接；集合空了就把数据源键也删掉，避免注册表留下空桶
     * —— 空桶会让 {@link #watchedIds()} 一直认为「有人在看」，探测停不下来。
     *
     * @param datasourceId 数据源 id
     * @param emitter      连接
     */
    private void remove(Long datasourceId, SseEmitter emitter) {
        Set<SseEmitter> set = subscribers.get(datasourceId);
        if (set == null) {
            return;
        }
        if (set.remove(emitter)) {
            log.info("[datasource-metric] SSE 订阅关闭 datasourceId={} 剩余连接数={}",
                    datasourceId, set.size());
        }
        if (set.isEmpty()) {
            subscribers.remove(datasourceId, set);
        }
    }

    /**
     * 心跳：给所有连接推注释帧保活。
     *
     * <p>nginx、内置容器与各类网关对空闲长连接都有 30~60s 超时，静默的 SSE 流
     * 会被中间层悄悄掐断，前端表现为「连上后过一会儿就不更新了」且不报错。</p>
     */
    @Scheduled(fixedRateString = "${monitor.datasource.sse-heartbeat-ms:20000}",
            initialDelayString = "${monitor.datasource.sse-heartbeat-ms:20000}")
    public void heartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Set<SseEmitter>> entry : subscribers.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    remove(entry.getKey(), emitter);
                }
            }
        }
    }

    /**
     * 当前某数据源的订阅连接数（诊断用）。
     *
     * @param datasourceId 数据源 id
     * @return 连接数
     */
    public int subscriberCount(Long datasourceId) {
        Set<SseEmitter> set = subscribers.get(datasourceId);
        return set == null ? 0 : set.size();
    }
}