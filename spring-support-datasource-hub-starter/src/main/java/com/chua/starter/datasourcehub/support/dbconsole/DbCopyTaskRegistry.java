package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 数据库管理台的后台任务流：复制表等长任务的进度广播。
 *
 * <p><b>为什么是「全局一条流」而不是「每个任务一条流」</b>：复制任务由前端发起后
 * 立刻返回 {@code taskId}，进度必须走一条与这次 HTTP 请求无关的通道，否则刷新页面
 * 就再也看不到进度。数据中心同时可能跑多个复制任务，一条全局流按 {@code taskId}
 * 分发给所有订阅者，前端按需过滤，比给每个任务各开一条长连接省得多。</p>
 *
 * <p><b>为什么手写 byte[] 帧</b>：本项目在此 Spring 版本（Boot 4.1 / Spring 7）下
 * {@code SseEmitter.data(对象)} 会被静默丢弃（event 行正常、data 恒空）。只有
 * {@code data(byte[])} 走的是直写分支，这一点由数据源指标推送
 * （{@code DatasourceMetricStreamRegistry}）实测验证过，这里沿用同一写法。</p>
 *
 * <p>快照字段刻意与 {@code packages/ui} 的 {@code ProgressSnapshot} 对齐：
 * 前端把事件原样喂给 {@code ReProgressDialog} 即可，不再做一层翻译。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
public class DbCopyTaskRegistry {

    /**
     * 任务快照事件：每次状态变化推一份完整快照（不是增量）。
     *
     * <p>推完整快照而不是增量，是因为前端可能在中途刷新页面：收到任意一帧都能
     * 直接渲染，不需要重放历史事件。</p>
     */
    public static final String EVENT_TASK = "task";

    /** 建连时推送当前全部任务，前端不必等下一次变化 */
    public static final String EVENT_READY = "ready";

    /** 保留的任务条数（含已结束）；超出后按结束时间淘汰最旧的 */
    private static final int MAX_TASKS = 40;

    /** SSE 连接超时：0 表示由心跳保活，不主动断开 */
    private static final long STREAM_TIMEOUT_MS = 0L;

    /** 单条描述流水最多保留行数，防止大表复制把内存吃光 */
    private static final int MAX_DETAIL_LINES = 200;

    /** 任务序号，只用于生成可读的 taskId */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** taskId → 任务；读写都在 {@link #tasks} 自身上加锁 */
    private final Map<String, CopyTask> tasks = new LinkedHashMap<>();

    /** SSE 订阅者：全局流，增删只在建连/断开时发生 */
    private final Set<SseEmitter> subscribers = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    /**
     * 复制任务的执行线程。
     *
     * <p>固定两条：复制是重 IO 操作，同时跑太多会把目标库的连接数与磁盘打满；
     * 两个并发足够覆盖「顺手再复制一张」的场景，超出排队。</p>
     */
    // [P3C C1 豁免]
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        // [P3C C1 豁免]
        Thread thread = new Thread(runnable, "db-copy-task");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 构造注入。
     *
     * @param objectMapper JSON 序列化器
     */
    @Autowired
    public DbCopyTaskRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 建立一条全局任务订阅。
     *
     * <p>建连先发 {@code ready}（当前全部任务快照），再收后续 {@code task} 事件。</p>
     *
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.add(emitter);
        log.info("[db-copy] 任务流订阅建立，连接数={}", subscribers.size());

        Runnable remove = () -> remove(emitter);
        emitter.onCompletion(remove);
        emitter.onError(e -> {
            log.debug("[db-copy] 任务流连接异常关闭: {}", e.getMessage());
            remove.run();
        });
        emitter.onTimeout(() -> {
            log.warn("[db-copy] 任务流连接超时");
            remove.run();
        });
        try {
            sendJson(emitter, EVENT_READY, Map.of("tasks", snapshots()));
        } catch (IOException e) {
            log.debug("[db-copy] 任务流初始帧发送失败: {}", e.getMessage());
            remove.run();
        }
        return emitter;
    }

    /**
     * 提交一个后台任务。
     *
     * @param runnable 任务体
     */
    public void submit(Runnable runnable) {
        executor.submit(runnable);
    }

    /**
     * 新建一个任务并广播。
     *
     * <p>步骤清单在建任务时就铺好：前端第一条推送到达之前也要能看到
     * 「这次会做哪几步」，否则进度弹窗在头几百毫秒里是一片空白。</p>
     *
     * @param title    任务标题（弹窗标题）
     * @param subtitle 副标题（源 → 目标）
     * @param steps    步骤：键 → 标题，按顺序
     * @return 任务 id
     */
    public String create(String title, String subtitle, Map<String, String> steps) {
        String taskId = "db-copy-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
        CopyTask task = new CopyTask(taskId, title, subtitle);
        for (Map.Entry<String, String> entry : steps.entrySet()) {
            task.steps.add(new Step(entry.getKey(), entry.getValue()));
        }
        task.currentTitle = title;
        task.currentDescription = "正在启动…";
        synchronized (tasks) {
            evictLocked();
            tasks.put(taskId, task);
        }
        publish(taskId);
        return taskId;
    }

    /**
     * 把某一步置为进行中，并把它之前的步骤全部标记为已完成。
     *
     * <p>顺序执行的步骤没必要让调用方逐个收尾；上一个阶段结束才可能进入下一个，
     * 这里按顺序兜底，省得漏一次收尾就出现两个「进行中」。</p>
     *
     * @param taskId      任务 id
     * @param stepKey     步骤键
     * @param description 这一步在做什么
     */
    public void startStep(String taskId, String stepKey, String description) {
        CopyTask task = task(taskId);
        if (task == null) {
            return;
        }
        synchronized (task) {
            boolean reached = false;
            for (Step step : task.steps) {
                if (step.key.equals(stepKey)) {
                    reached = true;
                    step.status = "RUNNING";
                    step.detail = description;
                } else if (!reached && "RUNNING".equals(step.status)) {
                    step.status = "SUCCESS";
                }
            }
            task.currentDescription = description;
            if (reached) {
                task.currentTitle = stepTitle(task, stepKey);
            }
        }
        publish(taskId);
    }

    /**
     * 结束某一步。
     *
     * @param taskId  任务 id
     * @param stepKey 步骤键
     * @param status  SUCCESS / SKIPPED / ERROR
     * @param detail  结果补充（错误原因 / 跳过原因），可为空
     */
    public void finishStep(String taskId, String stepKey, String status, String detail) {
        CopyTask task = task(taskId);
        if (task == null) {
            return;
        }
        synchronized (task) {
            for (Step step : task.steps) {
                if (step.key.equals(stepKey)) {
                    step.status = status;
                    step.detail = detail;
                }
            }
        }
        publish(taskId);
    }

    /**
     * 更新百分比与「现在正在做什么」。
     *
     * @param taskId      任务 id
     * @param percentage  0-100
     * @param description 当前描述
     */
    public void progress(String taskId, int percentage, String description) {
        CopyTask task = task(taskId);
        if (task == null) {
            return;
        }
        synchronized (task) {
            // [P3C A20 豁免]
            task.percentage = Math.max(0, Math.min(100, percentage));
            if (description != null && !description.isBlank()) {
                task.currentDescription = description;
                // 步骤副标题同步跟到最新进度：数据段每 500 行推一次「已复制 N / M 行」，
                // 这里不跟着写的话，任务被取消/失败收尾时步骤还挂着启动文案
                // 「统计源表行数」，和顶层「已取消复制」自相矛盾
                for (Step step : task.steps) {
                    if ("RUNNING".equals(step.status)) {
                        step.detail = description;
                    }
                }
            }
        }
        publish(taskId);
    }

    /**
     * 追加一行描述流水（不改变百分比）。
     *
     * @param taskId 任务 id
     * @param line   文本
     */
    public void detail(String taskId, String line) {
        CopyTask task = task(taskId);
        if (task == null || line == null || line.isBlank()) {
            return;
        }
        synchronized (task) {
            task.details.add(line);
            while (task.details.size() > MAX_DETAIL_LINES) {
                task.details.remove(0);
            }
        }
        publish(taskId);
    }

    /**
     * 任务结束（成功/失败/取消）。
     *
     * @param taskId  任务 id
     * @param status  SUCCESS / FAILED / CANCELLED
     * @param message 结论
     */
    public void finish(String taskId, String status, String message) {
        CopyTask task = task(taskId);
        if (task == null) {
            return;
        }
        synchronized (task) {
            task.status = status;
            task.message = message;
            task.finished = true;
            task.finishedAt = System.currentTimeMillis();
            if ("SUCCESS".equals(status)) {
                // [P3C A20 豁免]
                task.percentage = 100;
            }
            for (Step step : task.steps) {
                if ("RUNNING".equals(step.status)) {
                    // 失败时把「正在跑的那一步」记成 FAILED：记成 CANCELLED 会让弹窗
                    // 显示「创建目标表 已取消」，看上去像用户主动取消，和失败文案自相矛盾
                    step.status = "SUCCESS".equals(status) ? "SUCCESS"
                            : ("FAILED".equals(status) ? "FAILED" : "CANCELLED");
                }
            }
            task.currentDescription = message;
        }
        publish(taskId);
    }

    /**
     * 某任务是否已被取消。
     *
     * @param taskId 任务 id
     * @return true=调用方应尽快停止
     */
    public boolean cancelled(String taskId) {
        CopyTask task = task(taskId);
        if (task == null) {
            return true;
        }
        synchronized (task) {
            return task.cancelRequested;
        }
    }

    /**
     * 请求取消任务。
     *
     * <p>只置标志位，真正的停止由复制循环在每个批次边界检查 —— 数据库语句
     * 已经开始执行时，从外部强行中断线程只会把连接留在未知状态。</p>
     *
     * @param taskId 任务 id
     * @return 是否找到并置位
     */
    public boolean cancel(String taskId) {
        CopyTask task = task(taskId);
        if (task == null) {
            return false;
        }
        synchronized (task) {
            if (task.finished) {
                return false;
            }
            task.cancelRequested = true;
            task.currentDescription = "正在取消…";
        }
        publish(taskId);
        return true;
    }

    /**
     * 当前全部任务快照（诊断/初始帧用）。
     *
     * @return 快照列表
     */
    public List<Map<String, Object>> snapshots() {
        List<Map<String, Object>> list = new ArrayList<>();
        synchronized (tasks) {
            for (CopyTask task : tasks.values()) {
                list.add(task.toMap());
            }
        }
        return list;
    }

    /**
     * 心跳：空闲长连接会被各类网关在 30~60s 掐断，前端表现为「连上后过一会儿就不更新了」。
     */
    @Scheduled(fixedRate = 20_000L, initialDelay = 20_000L)
    public void heartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                remove(emitter);
            }
        }
    }

    /**
     * 应用关闭时停掉复制线程。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 广播某任务的最新快照。
     *
     * @param taskId 任务 id
     */
    private void publish(String taskId) {
        CopyTask task = task(taskId);
        if (task == null || subscribers.isEmpty()) {
            return;
        }
        Map<String, Object> payload = task.toMap();
        for (SseEmitter emitter : subscribers) {
            try {
                sendJson(emitter, EVENT_TASK, payload);
            } catch (IOException | IllegalStateException e) {
                log.debug("[db-copy] 任务流推送失败，摘除连接: {}", e.getMessage());
                remove(emitter);
            }
        }
    }

    /**
     * 序列化成 JSON 字节后作为事件帧发出（必须 byte[]，见类注释）。
     *
     * @param emitter 目标连接
     * @param event   事件名
     * @param payload 载荷
     * @throws IOException 序列化或写出失败
     */
    private void sendJson(SseEmitter emitter, String event, Object payload) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(payload);
        emitter.send(SseEmitter.event().name(event).data(json));
    }

    /**
     * 移除一条连接。
     *
     * @param emitter 连接
     */
    private void remove(SseEmitter emitter) {
        if (subscribers.remove(emitter)) {
            log.info("[db-copy] 任务流订阅关闭，剩余连接数={}", subscribers.size());
        }
    }

    /**
     * 取任务。
     *
     * @param taskId 任务 id
     * @return 任务，不存在返回 null
     */
    private CopyTask task(String taskId) {
        if (taskId == null) {
            return null;
        }
        synchronized (tasks) {
            return tasks.get(taskId);
        }
    }

    /**
     * 淘汰最旧的已结束任务（调用方必须持有 {@link #tasks} 锁）。
     */
    private void evictLocked() {
        if (tasks.size() < MAX_TASKS) {
            return;
        }
        Iterator<Map.Entry<String, CopyTask>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext() && tasks.size() >= MAX_TASKS) {
            CopyTask candidate = iterator.next().getValue();
            synchronized (candidate) {
                if (candidate.finished) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 取步骤标题。
     *
     * @param task    任务
     * @param stepKey 步骤键
     * @return 标题，找不到返回空串
     */
    private static String stepTitle(CopyTask task, String stepKey) {
        for (Step step : task.steps) {
            if (step.key.equals(stepKey)) {
                return step.title;
            }
        }
        return "";
    }

    /**
     * 单步骤。
     */
    private static final class Step {

        private final String key;

        private final String title;

        private String status = "WAIT";

        private String detail;

        private Step(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }

    /**
     * 任务快照（字段与前端 ProgressSnapshot 对齐）。
     */
    private static final class CopyTask {

        private final String taskId;

        private final String title;

        private final String subtitle;

        private final long startedAt = System.currentTimeMillis();

        private final List<Step> steps = new ArrayList<>();

        private final List<String> details = new ArrayList<>();

        private String status = "RUNNING";

        private int percentage;

        private String currentTitle = "";

        private String currentDescription = "";

        private String message;

        private boolean finished;

        private boolean cancelRequested;

        private long finishedAt;

        private CopyTask(String taskId, String title, String subtitle) {
            this.taskId = taskId;
            this.title = title;
            this.subtitle = subtitle;
        }

        /**
         * 转成前端 ProgressSnapshot 结构。
         *
         * @return 快照
         */
        private synchronized Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("taskId", taskId);
            map.put("title", title);
            map.put("subtitle", subtitle);
            map.put("status", status);
            map.put("percentage", percentage);
            map.put("currentTitle", currentTitle);
            map.put("currentDescription", currentDescription);
            map.put("message", message);
            map.put("finished", finished);
            map.put("cancellable", !finished);
            map.put("startedAt", startedAt);
            map.put("finishedAt", finishedAt);
            List<Map<String, Object>> stepList = new ArrayList<>();
            for (Step step : steps) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", step.key);
                item.put("title", step.title);
                item.put("status", step.status);
                item.put("detail", step.detail);
                stepList.add(item);
            }
            map.put("steps", stepList);
            map.put("details", new ArrayList<>(details));
            return map;
        }
    }
}
