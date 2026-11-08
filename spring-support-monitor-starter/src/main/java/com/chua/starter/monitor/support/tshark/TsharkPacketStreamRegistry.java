package com.chua.starter.monitor.support.tshark;

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
 * 抓包数据包的 SSE 订阅者注册表。
 *
 * <p>按<b>抓包会话 id</b> 分组推送：前端选中某个会话时才建连，只有该会话的新包会推过来。
 * 与 {@code NginxMetricStreamRegistry} 同构，心跳保活 / 死连接清理 / 载荷必须传
 * {@code byte[]} 这三条约定都照抄那边的设计，原因见该类的注释（{@code byte[]} 与
 * {@code ThreadLocal HttpServletRequest} 那个坑在这里同样成立：广播发生在
 * {@code onPool-worker-N} 线程上，线程里没有请求对象）。</p>
 *
 * <p><strong>为什么走 SSE 而不是已有的 socket.io</strong>：socket.io 推送是全量广播，
 * 客户端必须自己按 {@code dataId} 过滤；抓包是「一次可能同时开好几个会话」的场景，
 * 按会话分组能让服务端只推当前在看的那个。前端原本用 socket.io 订阅
 * {@code tshark-packet}，那条路径保留给会话状态变化，数据包改走这里。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
public class TsharkPacketStreamRegistry {

    /**
     * 事件名：建连就绪。前端 {@code addEventListener("ready")}。
     */
    public static final String EVENT_READY = "ready";

    /**
     * 事件名：新数据包。前端 {@code addEventListener("packet")}。
     */
    public static final String EVENT_PACKET = "packet";

    /**
     * 事件名：会话状态变化（停止 / 解析完成 / 删除）。前端 {@code addEventListener("session")}。
     */
    public static final String EVENT_SESSION = "session";

    /**
     * SSE 连接超时（毫秒）。设0 表示交给容器不超时，由心跳负责保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 会话级暂停标记：{@code true} 表示该会话的实时推送已挂起。
     *
     * <p>暂停时抓包进程与 PCAP 更新不受影响：包还在写进文件，下游的
     * 增量解析与入库照样跑，只有推到前端的 SSE 帧被掐断。恢复后从断点继续推送，
     * 不会丢包，也不存在「暂停期间数据比上班前还长」的断崖。</p>
     */
    private final Map<Long, Boolean> paused = new ConcurrentHashMap<>();

    /**
     * 订阅者：抓包会话 id → 该会话的连接集合。
     *
     * <p>用 {@link CopyOnWriteArraySet}：广播是高频读，增删只在建连 / 断开时发生，
     * 且两者并发。理由同 nginx 那份注册表。</p>
     */
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    @Autowired
    public TsharkPacketStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅（建连即回 ready，让前端能立刻点亮「已连接」而不是靠超时猜）。
     *
     * @param sessionId 抓包会话 id
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe(Long sessionId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        log.info("[tshark] SSE 订阅建立 sessionId={} 当前连接数={}", sessionId, set.size());

        Runnable remove = () -> remove(sessionId, emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[tshark] SSE 连接异常关闭 sessionId={} reason={}", sessionId, e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[tshark] SSE 连接超时 sessionId={}", sessionId);
            remove.run();
        });

        try {
            sendJson(emitter, EVENT_READY, Map.of("sessionId", sessionId));
        } catch (IOException e) {
            log.debug("[tshark] SSE 初始帧发送失败 sessionId={} reason={}", sessionId, e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 向某会话的所有订阅者广播一个事件。
     *
     * <p>单连接发送失败只摘掉该连接：抓包页可能被开在多个标签页，
     * 一个标签关掉不能让别的不停更。</p>
     *
     * @param sessionId 抓包会话 id
     * @param event     事件名
     * @param payload   载荷对象，交给 Jackson 序列化
     * @return 成功送达的连接数
     */
    public int broadcast(Long sessionId, String event, Object payload) {
        if (sessionId == null) {
            return 0;
        }
        Set<SseEmitter> set = subscribers.get(sessionId);
        if (set == null || set.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : set) {
            try {
                sendJson(emitter, event, payload);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("[tshark] SSE 推送失败，摘除连接 sessionId={} reason={}", sessionId, e.getMessage());
                remove(sessionId, emitter);
            }
        }
        return delivered;
    }

    /**
     * 把载荷序列化成 JSON 字节后作为一个事件帧发出。
     *
     * <p><strong>必须传 byte[]</strong>：项目在转换器链首注册了只认 byte[] 的
     * {@code BinaryHttpMessageConverter}，传对象会被它认领却写出 0 字节，
     * 传字符串会被 Jackson 二次编码套一层引号 —— 两种都表现为「事件到了但载荷是空」，
     * 详见 {@code NginxMetricStreamRegistry#sendJson} 的完整说明。</p>
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷对象
     * @throws IOException 序列化失败或连接已断
     */
    private void sendJson(SseEmitter emitter, String event, Object payload) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(payload);
        emitter.send(SseEmitter.event().name(event).data(json));
    }

    /**
     * 移除一条连接；集合空了就删掉键，避免留下空桶。
     * @param emitter 参数
     * @param sessionId 长整数
     * @return 返回值
    */
    private void remove(Long sessionId, SseEmitter emitter) {
        Set<SseEmitter> set = subscribers.get(sessionId);
        if (set == null) {
            return;
        }
        if (set.remove(emitter)) {
            log.info("[tshark] SSE 订阅关闭 sessionId={} 剩余连接数={}", sessionId, set.size());
        }
        if (set.isEmpty()) {
            subscribers.remove(sessionId, set);
        }
    }

    /**
     * 移除某会话的全部订阅（会话删除时调用，避免悬空连接继续收到心跳与重连尝试）。
     *
     * @param sessionId 抓包会话 id
     */
    public void removeAll(Long sessionId) {
        Set<SseEmitter> set = subscribers.remove(sessionId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[tshark] SSE 会话删除时关闭连接失败 sessionId={} reason={}", sessionId, e.getMessage());
            }
        }
        log.info("[tshark] 会话删除，已关闭 {} 条 SSE 订阅 sessionId={}", set.size(), sessionId);
    }

    /**
     * 暂停某会话的实时推送。
     *
     * <p>只挂起推给前端的 SSE 帧：抓包进程照跑、文件照样写、增量解析照进库，
     * 恢复后从断点继续推，不会丢包。</p>
     *
     * @param sessionId 抓包会话 id
     */
    public void pause(Long sessionId) {
        paused.put(sessionId, true);
        log.info("[tshark] 抓包暂停 sessionId={}", sessionId);
    }

    /**
     * 恢复某会话的实时推送。
     *
     * @param sessionId 抓包会话 id
     */
    public void resume(Long sessionId) {
        paused.remove(sessionId);
        log.info("[tshark] 抓包恢复 sessionId={}", sessionId);
    }

    /**
     * 某会话当前是否暂停。
     *
     * @param sessionId 抓包会话 id
     * @return 暂停则返回 true
     */
    public boolean isPaused(Long sessionId) {
        return Boolean.TRUE.equals(paused.get(sessionId));
    }

    /**
     * 心跳：给所有连接推注释帧保活。
     *
     * <p>容器与各类网关对空闲长连接普遍30~60s 超时，静默流会被中间层悄悄掐断，
     * 前端表现为「连上后过一会儿不更新了」且不报错。</p>
     */
    @Scheduled(fixedRate = 20_000L, initialDelay = 20_000L)
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
}