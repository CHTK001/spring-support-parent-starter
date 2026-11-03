package com.chua.starter.monitor.support.datasync;

import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同步任务的 Hook 通知器：把执行结果 / 每条数据以 JSON POST 推给任务配置的 HTTP 地址。
 *
 * <p><b>时机</b>（见 {@link SyncHookNotify}）：完成通知每轮一条（成功/失败都发）；增量模式可
 * 额外按「每条数据」推送。全量模式固定为完成通知。</p>
 *
 * <p><b>发送策略与失败语义</b>：</p>
 * <ul>
 *   <li>完成通知<b>同步发送</b>（超时 {@value #DONE_TIMEOUT_SECONDS}s）：每轮只有一条，
 *       同步发才能把「发送成功 / 失败」的结果如实写进本次执行的调试日志缓冲
 *       （日志必须在 runLogBuffer.end 之前写，end 之后会被静默丢弃）；</li>
 *   <li>每条数据通知走<b>有界异步队列</b>（2 线程、队列 5000）：逐条 HTTP 往返若内联在写入
 *       管线里会被慢接收端拖死同步吞吐；队列满时丢弃并记应用日志（丢的是通知不是数据）；</li>
 *   <li>任何通知失败<b>只记日志，不影响同步结果</b>——回调是旁路，不是数据面的一部分。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTaskHookNotifier {

    /**
     * 完成通知的请求超时（秒）
     */
    private static final int DONE_TIMEOUT_SECONDS = 5;

    /**
     * 行通知队列容量；满即丢并记日志
     */
    private static final int ROW_QUEUE_CAPACITY = 5000;

    /**
     * 行通知线程数
     */
    private static final int ROW_POOL_SIZE = 2;

    /**
     * 完成时刻格式
     */
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * JSON 序列化（Spring 上下文里的单例 ObjectMapper）
     */
    private final ObjectMapper objectMapper;

    /**
     * 共享 HTTP 客户端（JDK 自带，连接池复用）
     */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * 行通知异步池：核心=最大 2 线程，有界队列，满则丢弃
     */
    private final ThreadPoolExecutor rowPool = new ThreadPoolExecutor(
            ROW_POOL_SIZE, ROW_POOL_SIZE, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(ROW_QUEUE_CAPACITY),
            new HookThreadFactory(),
            (task, executor) -> log.warn("[sync-hook] 行通知队列已满（{}），丢弃一条行通知", executor.getQueue().size()));

    /**
     * 行通知序号（日志/排障用）
     */
    private final AtomicLong rowSeq = new AtomicLong();

    /**
     * 停机时停掉行通知池（不等待积压发送完成：回调丢了无所谓，数据已落库）
     */
    @PreDestroy
    public void shutdown() {
        rowPool.shutdownNow();
    }

    /**
     * 任务生效的通知时机：全量固定完成通知，增量按配置。
     *
     * @param task 任务
     * @return 生效时机
     */
    public SyncHookNotify effectiveNotify(MsSyncTask task) {
        if (SyncMode.from(task.getSyncTaskSyncMode()) != SyncMode.INCREMENTAL) {
            return SyncHookNotify.DONE;
        }
        return SyncHookNotify.from(task.getSyncTaskHookNotify());
    }

    /**
     * 发送完成通知（同步，成功/失败都发）。
     *
     * @param task    任务
     * @param trigger 触发方式
     * @param result  执行结果
     * @return 结果描述，用于写入执行日志；未配置地址返回 空 表示无需记日志
     */
    public String notifyDone(MsSyncTask task, String trigger, SyncTaskRunVO result) {
        String url = task.getSyncTaskHookUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "COMPLETION");
        payload.put("taskId", task.getId());
        payload.put("taskName", task.getSyncTaskName());
        payload.put("syncMode", SyncMode.from(task.getSyncTaskSyncMode()).code());
        payload.put("writeMode", task.getSyncTaskWriteMode());
        payload.put("trigger", trigger);
        payload.put("status", result.status());
        payload.put("readCount", result.readCount());
        payload.put("writeCount", result.writeCount());
        payload.put("costMs", result.costMs());
        payload.put("runTime", result.runTime() == null ? null : result.runTime().format(RUN_TIME));
        payload.put("lastSeq", task.getSyncTaskLastSeq());
        payload.put("message", result.message());
        return post(url, payload, Duration.ofSeconds(DONE_TIMEOUT_SECONDS));
    }

    /**
     * 异步推送一条数据（仅增量 + ROW 时机使用）。
     *
     * <p>行值先归一成 JSON 友好类型：源行里常见 {@code Timestamp} / {@code byte[]} 直接交给
     * Jackson 会序列化成对象垃圾甚至抛错，非 String/Number/Boolean 的一律转字符串原样推送。</p>
     *
     * @param task       任务
     * @param mappedRow  映射后的行（即写进目标表的数据）
     * @param seq        该行的增量字段值（可为 空）
     */
    public void notifyRow(MsSyncTask task, Map<String, Object> mappedRow, String seq) {
        String url = task.getSyncTaskHookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ROW");
        payload.put("taskId", task.getId());
        payload.put("taskName", task.getSyncTaskName());
        payload.put("syncMode", SyncMode.INCREMENTAL.code());
        payload.put("seq", seq);
        payload.put("rowNo", rowSeq.incrementAndGet());
        Map<String, Object> data = new LinkedHashMap<>(mappedRow.size());
        mappedRow.forEach((key, value) -> data.put(key, normalize(value)));
        payload.put("data", data);
        try {
            rowPool.execute(() -> {
                String outcome = post(url, payload, Duration.ofSeconds(DONE_TIMEOUT_SECONDS));
                if (outcome != null && outcome.startsWith("失败")) {
                    // 行级失败只进应用日志：写进执行缓冲会把 500 行上限瞬间刷满
                    log.warn("[sync-hook] 行通知发送失败, task={}, url={}, {}", task.getId(), url, outcome);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[sync-hook] 行通知池已关闭或队列满，丢弃一条行通知, task={}", task.getId());
        }
    }

    /**
     * POST 一次 JSON 通知并返回结果描述。
     *
     * @param url     目标地址
     * @param payload JSON 载荷
     * @param timeout 请求超时
     * @return {@code HTTP 200} 之类的结果描述；异常返回 {@code 失败：原因}
     */
    private String post(String url, Map<String, Object> payload, Duration timeout) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            return code >= 200 && code < 300 ? "HTTP " + code : "HTTP " + code + "（非 2xx）";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "失败：发送被中断";
        } catch (Exception e) {
            return "失败：" + rootMessage(e);
        }
    }

    /**
     * 把行值归一成 JSON 友好类型。
     *
     * @param value 原值
     * @return String/Number/Boolean/Null 原样，其余转字符串
     */
    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    /**
     * 取异常根因消息。
     *
     * @param e 异常
     * @return 根因消息
     */
    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /**
     * 守护线程工厂：行通知线程不阻塞 JVM 退出。
     */
    private static final class HookThreadFactory implements java.util.concurrent.ThreadFactory {

        /**
         * 线程序号
         */
        private final AtomicLong seq = new AtomicLong();

        @Override
        public Thread newThread(Runnable r) {
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
            Thread thread = new Thread(r, "sync-hook-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
