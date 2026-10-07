package com.chua.starter.eduai.stream;

import com.chua.starter.eduai.model.vo.MarkingProgressVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 阅卷进度 SSE 推送注册表。
 *
 * <p>模型与 nginx 指标流（{@code NginxMetricStreamRegistry}）一致：
 * 按任务业务标识维护「任务 → 活跃连接集合」，判分线程在每次
 * {@code changeStage} 时广播一帧，前端 {@code GET /v2/edu/marking/stream/{taskId}}
 * 即取即看，不再靠 2s 定时轮询问结果接口。</p>
 *
 * <p><strong>为什么每条流要能多连接</strong>：老师可能开多个标签页，
 * 也可能断线重连（重连期间旧连接尚未被容器回收）。广播失败的连接只摘掉自己一个，
 * 不影响同一任务下的其它订阅者。</p>
 *
 * <p><strong>载荷必须自己序列化成 byte[] 再发</strong>：平台在转换器链首注册的
 * {@code BinaryHttpMessageConverter} 会在媒体类型为 null 时认领所有类型，
 * 而它的 {@code writeInternal} 只对 {@code byte[]} 直接写出，对象 / 字符串都会
 * 静默写出 0 字节（前端收到事件但 data 恒为空）。详见
 * {@code NginxMetricStreamRegistry#sendJson} 的注释。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class EduMarkingProgressHub {

    /**
     * 单条流超时：判分逐题调模型，整单最慢也在分钟级；30 分钟覆盖「上传后离开再回来」的场景。
     */
    private static final long STREAM_TIMEOUT_MS = 30L * 60 * 1000;

    /**
     * 心跳间隔：客户端与反代常在 60s 空闲后掐流，注释帧维持连接。
     */
    private static final long KEEPALIVE_MS = 15_000L;

    /**
     * 订阅者：任务业务标识 → 该任务的活跃流集合。
     *
     * <p>用 {@link CopyOnWriteArraySet}：广播是高频读，增删只在建连 / 断开时发生，
     * 且判分线程广播与容器线程摘除并发。</p>
     */
    private final Map<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * JSON 序列化器（与普通 REST 接口同一口径）。
     */
    private final ObjectMapper objectMapper;

    /**
     * 心跳线程。
     *
     * <p>不依赖 {@code @Scheduled}：eduai 模块不保证全局开启调度（开启与否是宿主配置），
     * 自带一个守护线程更稳，不影响任何模块的装配。</p>
     */
    private final ScheduledExecutorService keepaliveExecutor;

    /**
     * 构造注入。
     *
     * @param objectMapper JSON 序列化器
     */
    public EduMarkingProgressHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.keepaliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "edu-marking-sse-keepalive");
            thread.setDaemon(true);
            return thread;
        });
        this.keepaliveExecutor.scheduleWithFixedDelay(
                this::heartbeat, KEEPALIVE_MS, KEEPALIVE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 建立一条订阅。
     *
     * @param taskId 任务业务标识
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Set<SseEmitter> set = subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);

        // 四个回调都指向同一个移除动作：连接无论以哪种方式结束都要从注册表摘掉
        Runnable remove = () -> remove(taskId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            log.debug("[EduAi] 阅卷进度流超时: taskId={}", taskId);
            remove.run();
        });
        emitter.onError(e -> {
            // 客户端关页也会走到这里，属于预期路径
            log.debug("[EduAi] 阅卷进度流关闭: taskId={}, reason={}", taskId,
                    e == null ? null : e.getMessage());
            remove.run();
        });
        log.debug("[EduAi] 阅卷进度流建立: taskId={}, 连接数={}", taskId, set.size());
        return emitter;
    }

    /**
     * 给单条连接发一帧（初始快照用）。
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷
     */
    public void sendTo(SseEmitter emitter, String event, Object payload) {
        try {
            sendJson(emitter, event, payload);
        } catch (IOException | IllegalStateException e) {
            log.debug("[EduAi] 阅卷进度初始帧发送失败: {}", e.getMessage());
        }
    }

    /**
     * 向某任务的所有订阅者广播一帧进度。
     *
     * @param taskId  任务业务标识
     * @param event   事件名
     * @param payload 载荷对象
     * @return 成功送达的连接数
     */
    public int broadcast(String taskId, String event, Object payload) {
        Set<SseEmitter> set = subscribers.get(taskId);
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
                log.debug("[EduAi] 阅卷进度推送失败，摘除连接: taskId={}, reason={}", taskId, e.getMessage());
                remove(taskId, emitter);
            }
        }
        return delivered;
    }

    /**
     * 广播终态并主动收流：老师侧无需等超时，前端收到后立即拉最终结果。
     *
     * @param taskId  任务业务标识
     * @param event   事件名（约定为 done）
     * @param payload 终态载荷
     */
    public void complete(String taskId, String event, Object payload) {
        broadcast(taskId, event, payload);
        Set<SseEmitter> set = subscribers.remove(taskId);
        if (set == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[EduAi] 关闭阅卷进度流失败: taskId={}, reason={}", taskId, e.getMessage());
            }
        }
    }

    /**
     * 当前某任务的订阅连接数（诊断用）。
     *
     * @param taskId 任务业务标识
     * @return 连接数
     */
    public int subscriberCount(String taskId) {
        Set<SseEmitter> set = subscribers.get(taskId);
        return set == null ? 0 : set.size();
    }

    /**
     * 把载荷序列化成 JSON 字节后作为一个事件帧发出。
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷对象
     * @throws IOException 序列化失败或连接已断
     */
    private void sendJson(SseEmitter emitter, String event, Object payload) throws IOException {
        // 必须传 byte[]：见类注释，对象 / 字符串会被 BinaryHttpMessageConverter 静默吞掉
        byte[] json = objectMapper.writeValueAsBytes(payload);
        emitter.send(SseEmitter.event().name(event).data(json));
    }

    /**
     * 移除一条连接；集合空了把任务键也删掉，避免注册表留空桶。
     *
     * @param taskId  任务业务标识
     * @param emitter 连接
     */
    private void remove(String taskId, SseEmitter emitter) {
        Set<SseEmitter> set = subscribers.get(taskId);
        if (set == null) {
            return;
        }
        if (set.remove(emitter)) {
            log.debug("[EduAi] 阅卷进度流关闭: taskId={}, 剩余连接数={}", taskId, set.size());
        }
        if (set.isEmpty()) {
            subscribers.remove(taskId, set);
        }
    }

    /**
     * 心跳：给所有连接推注释帧保活；帧体是注释行，前端共享客户端会丢弃空 data 事件。
     */
    private void heartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<SseEmitter>> entry : subscribers.entrySet()) {
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
     * 应用关闭时停掉心跳线程。
     */
    @PreDestroy
    public void shutdown() {
        keepaliveExecutor.shutdownNow();
    }
}
