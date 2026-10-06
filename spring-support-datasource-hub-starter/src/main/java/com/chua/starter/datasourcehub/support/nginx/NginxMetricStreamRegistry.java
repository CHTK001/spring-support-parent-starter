package com.chua.starter.datasourcehub.support.nginx;

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
 * Nginx 指标 / 运行态的 SSE 订阅者注册表。
 *
 * <p>浏览器 {@code EventSource} 只能单向收推送，且本项目所有 {@code /ms/**} 请求
 * 都带鉴权（token 同时走 {@code Authorization} 头与 {@code x-oauth-token} query），
 * 所以推送由服务端在采集轮次结束时主动发起，前端不轮询。</p>
 *
 * <p><strong>为什么必须定时心跳</strong>：nginx、Spring 内置容器与各类网关对
 * 空闲长连接都有 30~60s 的超时，静默的 SSE 流会被中间层悄悄掐断，前端表现为
 * 「连上后过一会儿就不更新了」且不报任何错。每 {@code monitor.nginx.sse-heartbeat-ms}
 * 推一次注释帧（{@code :keep-alive}），既能保活，也能让前端识别出链路已死。</p>
 *
 * <p><strong>死连接必须清理</strong>：浏览器标签页一关，对端就是半开连接，
 * 服务端 send 时才抛 {@code IOException}。这里在 send 失败与
 * onCompletion / onError / onTimeout 四个回调里都做移除，避免注册表无限膨胀。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
public class NginxMetricStreamRegistry {

    /**
     * 事件名：指标新点。前端 {@code addEventListener("metric")}。
     */
    public static final String EVENT_METRIC = "metric";

    /**
     * 事件名：实时运行态。前端 {@code addEventListener("runtime")}。
     */
    public static final String EVENT_RUNTIME = "runtime";

    /**
     * SSE 连接超时（毫秒）。设为 0 表示交给容器不超时，由心跳负责保活。
     */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /**
     * 订阅者：数据源 id → 该数据源的连接集合。
     *
     * <p>用 {@link CopyOnWriteArraySet} 而不是 {@code HashSet}：广播是高频读操作，
     * 而增删只在连接建立 / 断开时发生，读多写少，且广播线程与移除线程并发。
     * {@code CopyOnWriteArraySet} 的读无锁，且 {@code remove} 线程安全。</p>
     */
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * JSON 序列化器。
     *
     * <p>用来把推送载荷先转成字符串，见 {@link #sendJson} 的说明。</p>
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造注入。
     *
     * @param objectMapper JSON 序列化器
     */
    @Autowired
    public NginxMetricStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条订阅。
     *
     * <p>建连后立刻推一个 {@code ready} 事件，让前端能在收到业务数据之前
     * 就把「已连接」状态点亮——否则前端只能靠超时猜链路是否建立成功。</p>
     *
     * @param datasourceId 数据源 id
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe(Long datasourceId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(datasourceId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        log.info("[nginx] SSE 订阅建立 datasourceId={} 当前连接数={}", datasourceId, set.size());

        // 四个回调都指向同一个移除动作：连接无论以哪种方式结束都要从注册表摘掉
        Runnable remove = () -> remove(datasourceId, emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            // 客户端正常关页也会走到这里，属于预期路径，只记 debug
            log.debug("[nginx] SSE 连接异常关闭 datasourceId={} reason={}", datasourceId, e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[nginx] SSE 连接超时 datasourceId={}", datasourceId);
            remove.run();
        });

        try {
            sendJson(emitter, "ready", Map.of("datasourceId", datasourceId));
        } catch (IOException e) {
            log.debug("[nginx] SSE 初始帧发送失败 datasourceId={} reason={}", datasourceId, e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 向某数据源的所有订阅者广播一个事件。
     *
     * <p>单个连接发送失败只摘掉该连接，不影响其它订阅者：多个运维同时开着
     * 控制台是常态，一个标签页关掉不能让别人的图停更。</p>
     *
     * @param datasourceId 数据源 id
     * @param event        事件名（前端 addEventListener 订阅）
     * @param payload      载荷对象，交给 Jackson 序列化
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
                log.debug("[nginx] SSE 推送失败，摘除连接 datasourceId={} reason={}",
                        datasourceId, e.getMessage());
                remove(datasourceId, emitter);
            }
        }
        return delivered;
    }

    /**
     * 把载荷序列化成 JSON 字节后作为一个事件帧发出。
     *
     * <p><strong>这里必须传 byte[]：传对象和传字符串都会静默丢数据</strong>，
     * 而两种丢法都表现为「事件收到、载荷为空」，排查时极具迷惑性。
     * 根因是本项目在转换器链首注册了自定义的
     * {@code BinaryHttpMessageConverter}（spring-support-common-starter）：
     * 它声明支持的媒体类型只有 {@code application/encrypted-data}，
     * 但 Spring 的 {@code canWrite(clazz, null)} 在媒体类型为 null 时会短路成
     * {@code canWriteInternal}，于是它<strong>认领所有类型</strong>。
     * 而它的 {@code writeInternal} 只对 {@code byte[]} 直接写出，
     * 其余分支都依赖 {@code RequestUtils.getRequest()} 拿到的 ThreadLocal 请求对象。</p>
     *
     * <p>三种写法实测下来的结果：</p>
     * <ul>
     *   <li>{@code data(payload)}（对象，媒体类型为 null）→ 被它认领；
     *       而广播发生在 {@code scheduling-N} 线程上，线程里没有
     *       {@code HttpServletRequest}，它打一条 warn
     *       「无法获取 HttpServletRequest, 跳过写入」就返回，
     *       <strong>写出 0 字节</strong>——帧里只剩 {@code event:metric} 加一个空
     *       {@code data:}。浏览器 {@code EventSource} 一切正常、不报错、
     *       事件按名字照常分发，唯独 {@code e.data} 恒为空串，
     *       看起来就像「后端连上了却不采集」。</li>
     *   <li>{@code data(json, APPLICATION_JSON)}（字符串加具体媒体类型）→ 它的
     *       {@code canWrite} 返回 false，落到 Jackson；但 Jackson 会把「已经序列化好的
     *       字符串」当成一个普通对象<strong>再转一次</strong>，输出成
     *       {@code data:"{\"datasourceId\":1}"}，整个载荷被套上一层引号变成 JSON 字符串。
     *       前端 {@code JSON.parse} 得到的是 String，{@code payload.points} 恒为
     *       {@code undefined}，数据「到了但一条都进不去」。</li>
     *   <li>{@code data(bytes)}（byte[]）→ 命中它 {@code writeInternal} 的第一个分支，
     *       直接 {@code outputMessage.getBody().write(bytes)}，
     *       <strong>既不碰 ThreadLocal 请求、也不再经过 Jackson</strong>，
     *       输出的就是我们想要的原始 JSON。这是唯一无歧义的路径。</li>
     * </ul>
     *
     * <p>自己序列化还有个附带好处：JSON 字节由项目自己的 ObjectMapper 产出，
     * 口径和普通 REST 接口一致，不会两边漂移。</p>
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷对象
     * @throws IOException 序列化失败或连接已断
     */
    private void sendJson(SseEmitter emitter, String event, Object payload) throws IOException {
        // Jackson 默认输出单行紧凑 JSON、不含裸换行；
        // SSE 规范要求多行 data 每个换行前补 data:，这里天然不会触发
        byte[] json = objectMapper.writeValueAsBytes(payload);
        emitter.send(SseEmitter.event().name(event).data(json));
    }

    /**
     * 移除一条连接；集合空了就把数据源键也删掉，避免注册表留下空桶。
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
            log.info("[nginx] SSE 订阅关闭 datasourceId={} 剩余连接数={}", datasourceId, set.size());
        }
        if (set.isEmpty()) {
            subscribers.remove(datasourceId, set);
        }
    }

    /**
     * 心跳：给所有连接推注释帧保活。
     *
     * <p>帧体是 {@code :keep-alive} 这种注释行而非 {@code data:} 事件，
     * 前端共享 SSE 客户端已显式丢弃无 data 的空事件，不会误清状态。</p>
     */
    @Scheduled(fixedRateString = "${monitor.nginx.sse-heartbeat-ms:20000}",
            initialDelayString = "${monitor.nginx.sse-heartbeat-ms:20000}")
    public void heartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        int total = 0;
        for (Map.Entry<Long, Set<SseEmitter>> entry : subscribers.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                    total++;
                } catch (IOException | IllegalStateException e) {
                    remove(entry.getKey(), emitter);
                }
            }
        }
        if (total > 0) {
            log.trace("[nginx] SSE 心跳完成 连接数={}", total);
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
