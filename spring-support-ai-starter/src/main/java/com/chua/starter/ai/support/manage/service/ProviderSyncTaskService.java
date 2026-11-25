// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 渠道同步异步任务注册表。
 *
 * <p>{@code POST /api/providers/{id}/sync} 的全量同步（上游目录拉取、限制探测、
 * 描述 / 计费 / 目录 / 余额落库）耗时可达分钟级，不再让 HTTP 请求同步等待：
 * 提交即返回 {@code task_id}，前端轮询 {@code GET /api/provider-sync-tasks/{task_id}}。</p>
 *
 * <p>任务状态保存在本进程内存（{@code ConcurrentHashMap}），重启即失——同步本身
 * 幂等，重启后重新提交即可。线程池有界：核心 2、上限 4、队列 32，队列满时
 * 任务直接标记 {@link Status#FAILED}，不阻塞提交方。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
public class ProviderSyncTaskService {

    /**
     * 任务状态
     */
    public enum Status {
        /** 已提交待执行 */
        PENDING,
        /** 执行中 */
        RUNNING,
        /** 执行成功（result 为同步结果分节） */
        SUCCESS,
        /** 执行失败（error 为失败原因） */
        FAILED
    }

    /**
     * 已完成任务保留时长（毫秒）：超时即清理，防注册表无限增长
     */
    private static final long RETAIN_MILLIS = TimeUnit.MINUTES.toMillis(30);

    /**
     * 注册表容量软上限：超出后优先清理最旧的已完成任务
     */
    private static final int MAX_TASKS = 500;

    /**
     * 任务线程序号
     */
    private static final AtomicLong SEQ = new AtomicLong();

    /**
     * 任务注册表
     */
    private final Map<String, SyncTask> tasks = new ConcurrentHashMap<>();

    /**
     * 同步执行线程池（守护线程，随宿主退出）
     */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(32),
            r -> {
                Thread t = new Thread(r, "provider-sync-" + SEQ.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * 提交一个同步任务，立即返回任务句柄。
     *
     * @param providerId 渠道 id（记录与查询归属）
     * @param job        同步作业（由调用方绑定事务代理与参数）
     * @return 任务句柄（PENDING 态）
     */
    public SyncTask submit(String providerId, Callable<Map<String, Object>> job) {
        evictExpired();
        SyncTask task = new SyncTask(providerId);
        tasks.put(task.getTaskId(), task);
        try {
            executor.execute(() -> run(task, job));
        } catch (Exception e) {
            // 线程池/队列拒绝：任务落 FAILED，提交方仍可查询到失败原因
            task.fail("同步任务队列已满，请稍后重试: " + e.getMessage());
        }
        return task;
    }

    /**
     * 查询任务。
     *
     * @param taskId 任务 id
     * @return 任务句柄；不存在返回 {@code null}
     */
    public SyncTask get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return tasks.get(taskId);
    }

    /**
     * 列出某渠道的全部任务（新→旧）。
     *
     * @param providerId 渠道 id；空返回全部
     * @return 任务列表
     */
    public List<SyncTask> list(String providerId) {
        List<SyncTask> all = new ArrayList<>(tasks.values());
        all.removeIf(t -> providerId != null && !providerId.isBlank()
                && !providerId.equals(t.getProviderId()));
        all.sort(Comparator.comparingLong(SyncTask::getSubmittedAt).reversed());
        return all;
    }

    /**
     * 工作线程执行体：PENDING → RUNNING → SUCCESS/FAILED。
     *
     * @param task 任务
     * @param job  同步作业
     */
    private void run(SyncTask task, Callable<Map<String, Object>> job) {
        task.start();
        try {
            Map<String, Object> result = job.call();
            task.succeed(result);
            log.info("[ProviderSyncTask] task={} provider={} SUCCESS", task.getTaskId(), task.getProviderId());
        } catch (Exception e) {
            log.warn("[ProviderSyncTask] task={} provider={} FAILED: {}",
                    task.getTaskId(), task.getProviderId(), e.getMessage());
            task.fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * 清理过期与超容量的已完成任务。
     */
    private void evictExpired() {
        long cutoff = System.currentTimeMillis() - RETAIN_MILLIS;
        tasks.values().removeIf(t -> t.isFinished() && t.getFinishedAt() < cutoff);
        if (tasks.size() <= MAX_TASKS) {
            return;
        }
        List<SyncTask> finished = new ArrayList<>();
        for (SyncTask t : tasks.values()) {
            if (t.isFinished()) {
                finished.add(t);
            }
        }
        finished.sort(Comparator.comparingLong(SyncTask::getFinishedAt));
        int remove = tasks.size() - MAX_TASKS;
        for (int i = 0; i < remove && i < finished.size(); i++) {
            tasks.remove(finished.get(i).getTaskId());
        }
    }

    /**
     * 释放线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 同步任务句柄（线程安全：状态与结果字段 volatile）。
     */
    @Getter
    public static final class SyncTask {

        /**
         * 任务 id
         */
        private final String taskId;
        /**
         * 渠道 id
         */
        private final String providerId;
        /**
         * 提交时间（毫秒）
         */
        private final long submittedAt;
        /**
         * 当前状态
         */
        private volatile Status status = Status.PENDING;
        /**
         * 开始执行时间（毫秒）；未开始为 0
         */
        private volatile long startedAt = 0L;
        /**
         * 结束时间（毫秒）；未结束为 0
         */
        private volatile long finishedAt = 0L;
        /**
         * 同步结果（SUCCESS 时）
         */
        private volatile Map<String, Object> result;
        /**
         * 失败原因（FAILED 时）
         */
        private volatile String error;

        /**
         * 创建任务。
         *
         * @param providerId 渠道 id
         */
        private SyncTask(String providerId) {
            this.taskId = UUID.randomUUID().toString().replace("-", "");
            this.providerId = providerId;
            this.submittedAt = System.currentTimeMillis();
        }

        /**
         * 标记开始执行。
         */
        private void start() {
            this.status = Status.RUNNING;
            this.startedAt = System.currentTimeMillis();
        }

        /**
         * 标记成功。
         *
         * @param result 同步结果
         */
        private void succeed(Map<String, Object> result) {
            this.result = result;
            this.status = Status.SUCCESS;
            this.finishedAt = System.currentTimeMillis();
        }

        /**
         * 标记失败。
         *
         * @param message 失败原因
         */
        private void fail(String message) {
            this.error = message;
            this.status = Status.FAILED;
            this.finishedAt = System.currentTimeMillis();
        }

        /**
         * 是否已结束（SUCCESS / FAILED）。
         *
         * @return 已结束返回 {@code true}
         */
        public boolean isFinished() {
            return status == Status.SUCCESS || status == Status.FAILED;
        }
    }
}
