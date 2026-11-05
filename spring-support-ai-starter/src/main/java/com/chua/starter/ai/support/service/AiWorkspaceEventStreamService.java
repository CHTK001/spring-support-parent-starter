package com.chua.starter.ai.support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话工作目录的文件变化事件流（SSE）。
 *
 * <p>前端打开工作区侧栏时按会话连上来（{@code GET /v2/sessions/{id}/workspace/events}），
 * 智能体往工作目录里写文件时就地推送变化，让文件树与预览自动刷新，不必手动刷新。
 * 事件形状由前端契约钉死：{@code {type, path, sessionId, timestamp}}，
 * 其中 {@code path} 是**相对工作目录**的路径（统一用 {@code /} 分隔），{@code sessionId}
 * 原样回传请求里的会话标识（前端按它过滤，不认解析后的数字 ID）。</p>
 *
 * <p>为什么用轮询而不是 {@code WatchService}：工作目录是每账号一个的普通目录，
 * 递归注册新子目录、处理跨平台事件丢失的复杂度都换不来好处；一次扫描只读
 * {@code lastModified/size}，一秒钟一次、条目数有上限，代价可忽略，而行为在
 * Windows/Linux 上完全一致（部署机与开发机都是 Windows 环境下的差异敏感场景）。</p>
 *
 * <p>资源口径：每条连接一份订阅，连接断开/超时/发送失败都由回调注销，不留悬挂；
 * 共享单个守护线程轮询全部订阅，不给每个连接开线程。上限 256 条，超了踢最久没发过
 * 消息的那条（客户端 3 秒后自动重连，代价只是重新握手）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class AiWorkspaceEventStreamService {

    /**
     * 轮询间隔
     */
    private static final long POLL_MILLIS = 1000L;

    /**
     * 无变化时的心跳间隔（同时用来发现已死连接）
     */
    private static final long HEARTBEAT_MILLIS = 15000L;

    /**
     * 连接最长存活时间；到点由服务端收流，前端 3 秒后重连
     */
    private static final long EMITTER_TIMEOUT_MILLIS = 1_800_000L;

    /**
     * 单次扫描的条目上限（超出只监听前半部分，防止异常目录拖垮轮询）
     */
    private static final int MAX_ENTRIES = 20_000;

    /**
     * 并发订阅上限
     */
    private static final int MAX_SUBSCRIPTIONS = 256;

    /**
     * 与产物/依赖相关的目录不进事件流（变了也没人看，只浪费扫描）
     */
    private static final Set<String> SKIP_DIRS = Set.of(".git", "node_modules", "__pycache__", ".venv", "venv", ".idea");

    /**
     * 活跃订阅：key = 订阅 ID
     */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * 共享轮询线程（单线程即可：任务只是读目录 + 往 SSE 流里写）
     */
    private ScheduledExecutorService scheduler;

    /**
     * 项目统一的 ObjectMapper：自己序列化成字节再发，序列化口径与普通 REST 出口一致
     */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 项目统一的 ObjectMapper（与普通 REST 出口同一套序列化口径）
     */
    public AiWorkspaceEventStreamService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 启动轮询
     */
    @PostConstruct
    public void start() {
// [P3C C1 豁免] 场景化创建线程，需文档化
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
            Thread thread = new Thread(r, "ai-workspace-event-watch");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::poll, POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 停轮询并收掉所有连接
     */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (Subscription sub : subscriptions.values()) {
            try {
                sub.emitter.complete();
            } catch (Exception ignore) {
                // 已断开，无需处理
            }
        }
        subscriptions.clear();
    }

    /**
     * 订阅一个工作目录的文件变化。
     *
     * <p>首帧心跳交给轮询线程发：返回前直接 {@code send} 还没绑定到异步响应上，
     * 是否可用取决于 Spring 内部时序，没必要赌。</p>
     *
     * @param sessionKey 会话标识（原样回填进事件的 sessionId 字段）
     * @param root       工作目录；不存在时先按空目录监听，等它出现再报新增
     * @return SSE 流
     */
    public SseEmitter subscribe(String sessionKey, Path root) {
        Subscription sub = new Subscription(UUID.randomUUID().toString(), sessionKey, root);
        sub.snapshot = snapshot(root);
        sub.lastSentAt = System.currentTimeMillis();
        evictIfFull();
        subscriptions.put(sub.id, sub);
        sub.emitter.onCompletion(() -> subscriptions.remove(sub.id));
        sub.emitter.onTimeout(() -> {
            subscriptions.remove(sub.id);
            try {
                sub.emitter.complete();
            } catch (Exception ignore) {
                // 已断开
            }
        });
        sub.emitter.onError(e -> subscriptions.remove(sub.id));
        log.info("[workspace-events] 新订阅 session={} root={} 初始条目={}", sessionKey, root, sub.snapshot.size());
        return sub.emitter;
    }

    /**
     * 轮询一轮：逐个订阅算差异并推送，异常的直接注销
     */
    private void poll() {
        for (Subscription sub : subscriptions.values()) {
            try {
                tick(sub);
            } catch (Exception e) {
                log.warn("[workspace-events] 推送失败，注销订阅 {}: {}", sub.id, e.toString());
                subscriptions.remove(sub.id);
                try {
                    sub.emitter.completeWithError(e);
                } catch (Exception ignore) {
                    // 已断开
                }
            }
        }
    }

    /**
     * 单个订阅的一次扫描与推送
     *
     * @param sub 订阅
     * @throws IOException 推送失败（连接已断）
     */
    private void tick(Subscription sub) throws IOException {
        Map<String, Stamp> now = snapshot(sub.root);
        Map<String, Stamp> prev = sub.snapshot;
        List<Map<String, Object>> events = new ArrayList<>();
        for (Map.Entry<String, Stamp> entry : now.entrySet()) {
            Stamp old = prev.get(entry.getKey());
            if (old == null) {
                events.add(event(sub, entry.getValue().dir() ? "addDir" : "add", entry.getKey()));
            } else if (!old.equals(entry.getValue()) && !entry.getValue().dir()) {
                // 目录自身的 mtime 会因为子项增删而变，那由子项事件负责，不重复报
                events.add(event(sub, "change", entry.getKey()));
            }
        }
        for (Map.Entry<String, Stamp> entry : prev.entrySet()) {
            if (!now.containsKey(entry.getKey())) {
                Stamp old = entry.getValue();
                events.add(event(sub, old.dir() ? "unlinkDir" : "unlink", entry.getKey()));
            }
        }

        long nowMs = System.currentTimeMillis();
        if (!events.isEmpty()) {
            for (Map<String, Object> one : events) {
                send(sub, one);
            }
            sub.snapshot = now;
            sub.lastSentAt = nowMs;
        } else if (nowMs - sub.lastSentAt >= HEARTBEAT_MILLIS) {
            Map<String, Object> beat = new LinkedHashMap<>();
            beat.put("type", "heartbeat");
            beat.put("sessionId", sub.sessionKey);
            beat.put("timestamp", nowMs);
            send(sub, beat);
            sub.lastSentAt = nowMs;
        }
    }

    /**
     * 组装一条变化事件
     *
     * @param sub  订阅
     * @param type add/addDir/change/unlink/unlinkDir
     * @param path 相对工作目录的路径
     * @return 事件载荷
     */
    private Map<String, Object> event(Subscription sub, String type, String path) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("path", path);
        payload.put("sessionId", sub.sessionKey);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    /**
     * 推一帧；失败抛出由调用方注销订阅。
     *
     * <p><strong>必须传 {@code byte[]}：传对象和传字符串都会静默丢数据</strong>，
     * 两种丢法都表现为「事件到了、载荷为空」。根因是本项目在转换器链首注册了
     * {@code BinaryHttpMessageConverter}（spring-support-common-starter）：它声明的
     * 媒体类型只有 {@code application/encrypted-data}，但 Spring 的
     * {@code canWrite(clazz, null)} 在媒体类型为 null 时会短路成 {@code canWriteInternal}，
     * 于是它<strong>认领所有类型</strong>；而它的 {@code writeInternal} 只对
     * {@code byte[]} 直接写出，其余分支都要靠 ThreadLocal 拿
     * {@code HttpServletRequest} —— 推送发生在守护轮询线程上，线程里没有请求对象，
     * 它打一条「无法获取 HttpServletRequest, 跳过写入」就返回，<strong>写出 0 字节</strong>，
     * 帧里只剩一个空 {@code data:}（本服务实测到的正是这个现象：curl 收到 14 字节、
     * 两个空帧）。传字符串 + 明确媒体类型虽能绕开它，Jackson 又会把已序列化好的
     * 字符串再包一层引号。传 {@code byte[]} 命中它的第一个分支直接写流，
     * 既不碰 ThreadLocal、也不再经过 Jackson —— 与
     * {@code NginxMetricStreamRegistry#sendJson} 同一口径。</p>
     *
     * @param sub     订阅
     * @param payload JSON 载荷
     * @throws IOException 序列化失败或连接已断
     */
    private void send(Subscription sub, Map<String, Object> payload) throws IOException {
        // Jackson 默认单行紧凑输出，不含裸换行，SSE 帧不会被截断
        byte[] json = objectMapper.writeValueAsBytes(payload);
        sub.emitter.send(SseEmitter.event().data(json));
    }

    /**
     * 订阅数到上限时踢掉最久没推过消息的那条（客户端会自动重连）
     */
    private void evictIfFull() {
        if (subscriptions.size() < MAX_SUBSCRIPTIONS) {
            return;
        }
        Subscription oldest = null;
        for (Subscription sub : subscriptions.values()) {
            if (oldest == null || sub.lastSentAt < oldest.lastSentAt) {
                oldest = sub;
            }
        }
        if (oldest == null) {
            return;
        }
        subscriptions.remove(oldest.id);
        log.warn("[workspace-events] 订阅数达到上限 {}，踢掉最旧的一条 {}", MAX_SUBSCRIPTIONS, oldest.id);
        try {
            oldest.emitter.complete();
        } catch (Exception ignore) {
            // 已断开
        }
    }

    /**
     * 扫描目录，产出「相对路径 → 状态」快照。
     *
     * <p>目录不存在或不可读时返回空表（不算错误：工作目录通常是跑起来之后才创建的），
     * 条目数封顶 {@link #MAX_ENTRIES}，跳过依赖/产物目录。</p>
     *
     * @param root 工作目录
     * @return 快照
     */
    private Map<String, Stamp> snapshot(Path root) {
        Map<String, Stamp> map = new HashMap<>();
        if (root == null || !Files.isDirectory(root)) {
            return map;
        }
        final int[] count = {0};
        final boolean[] overflow = {false};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path rel = root.relativize(dir);
                    if (rel.getNameCount() > 0 && SKIP_DIRS.contains(rel.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (count[0] >= MAX_ENTRIES) {
                        overflow[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    count[0]++;
                    if (rel.getNameCount() > 0) {
                        map.put(relative(root, dir), new Stamp(true, attrs.lastModifiedTime().toMillis(), 0L));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (count[0] >= MAX_ENTRIES) {
                        overflow[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    count[0]++;
                    map.put(relative(root, file), new Stamp(false, attrs.lastModifiedTime().toMillis(), attrs.size()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // 删到一半、权限不足之类的瞬时问题：跳过该节点，下一轮再看
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("[workspace-events] 扫描失败 {}: {}", root, e.toString());
            return map;
        }
        if (overflow[0]) {
            log.warn("[workspace-events] 目录条目超过 {}，只监听前半部分: {}", MAX_ENTRIES, root);
        }
        return map;
    }

    /**
     * 相对路径，统一成前端要的 {@code /} 分隔
     *
     * @param root 工作目录
     * @param path 绝对路径
     * @return 相对路径
     */
    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /**
     * 一次扫描里单个节点的状态（目录不看 size）
     *
     * @param dir   是否目录
     * @param mtime 修改时间（毫秒）
     * @param size  文件大小
     */
    private record Stamp(boolean dir, long mtime, long size) {
    }

    /**
     * 一条 SSE 订阅
     *
     * @param id         订阅 ID
     * @param sessionKey 会话标识（回填进事件）
     * @param root       工作目录
     */
    private static final class Subscription {
        /**
         * 订阅 ID
         */
        private final String id;
        /**
         * 会话标识
         */
        private final String sessionKey;
        /**
         * 工作目录
         */
        private final Path root;
        /**
         * SSE 连接
         */
        private final SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        /**
         * 上一轮扫描结果
         */
        private volatile Map<String, Stamp> snapshot = new HashMap<>();
        /**
         * 最近一次推送时间（心跳用）
         */
        private volatile long lastSentAt;

        private Subscription(String id, String sessionKey, Path root) {
            this.id = id;
            this.sessionKey = sessionKey;
            this.root = root;
        }
    }}