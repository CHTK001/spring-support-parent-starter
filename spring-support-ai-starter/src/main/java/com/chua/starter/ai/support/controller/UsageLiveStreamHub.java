package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.model.vo.AiUsageModelTrendPointVO;
import com.chua.starter.ai.support.service.SysAiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用量「实时」推送中枢。
 *
 * <p><b>要解决什么：</b>用量页此前只在切筛选/点刷新时拉一次快照，一次真实调用
 * （尤其是 agent 一轮几十次工具调用）在界面上完全看不出来 —— 用户盯着页面，
 * Token 数一动不动，根本不知道系统在跑、跑了多少。用 SSE 把增量推上去，
 * 页面顶部给出「实时速率」（token/秒）与今日累计。</p>
 *
 * <p><b>为什么不是「每条记录推一次」：</b>高频场景（一个 agent 任务几十上百次调用）
 * 会把 SSE 通道打满，前端每条都要做一次响应式更新，页面反而卡住。这里改成
 * <b>合并推送</b>：写入方只置一个 dirty 标记，定时任务最多 {@value #flushIntervalMs}
 * 毫秒扫一次，有变化才重算并推。推送的是重算后的**全量快照**而不是增量，
 * 前端直接替换即可 —— 丢一帧也只是慢一拍，不会像增量那样越错越偏。</p>
 *
 * <p><b>为什么重算是安全的：</b>统计走 {@code sys_ai_usage_call_time} 索引的
 * 单条聚合 SQL，1 秒 1 次（且同一时间窗只算一次）完全在可接受范围内，
 * 换来的是「前端永远显示一个自洽的数」，不用在前端做补偿逻辑。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageLiveStreamHub {

    /**
     * 合并推送间隔（毫秒）。1 秒是「看起来实时」与「不打爆通道」之间的折中：
     * 更快对读数没有意义（人眼分辨不出），更慢会让长任务显得卡死。
     */
    private static final long flushIntervalMs = 1000L;

    /**
     * 单个订阅者的超时时间。
     *
     * <p>给 0L = 永不超时，配合前端在卸载时 {@code close()} 显式断开；
     * 若让代理按默认超时掐断，前端会在没有任何提示的情况下静默停止更新，
     * 页面上的「实时」标识还一直亮着 —— 比没有实时更糟。</p>
     */
    private static final long emitterTimeoutMs = 0L;

    /**
     * 用量服务：重算快照用。
     */
    private final SysAiUsageService usageService;

    /**
     * 已连接的订阅者。
     */
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /**
     * 自增版本号。写入方 {@code markDirty()} 自增，flush 时比对，
     * 只有版本变化才真的去查库并推送。
     */
    private final AtomicLong version = new AtomicLong();

    /**
     * 定时任务是否已启用。单元测试/精简部署下关闭定时器，由写入方直接触发。
     */
    @Value("${spring.ai.usage.live-stream.enabled:true}")
    private boolean scheduledEnabled = true;

    /**
     * 一次订阅：谁订阅了哪个时间窗。
     *
     * @param emitter   推送通道
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（不含）
     * @param userId    归属用户；null 表示统计全部用户
     */
    private record Subscriber(SseEmitter emitter, LocalDateTime startTime,
                              LocalDateTime endTime, String userId) {
    }

    /**
     * 订阅实时用量流，并立即推送一次当前快照。
     *
     * @param startTime 起始时间（含），可为空
     * @param endTime   结束时间（不含），可为空
     * @param userId    归属用户；空表示统计全部用户
     * @return SSE 通道
     */
    public SseEmitter subscribe(LocalDateTime startTime, LocalDateTime endTime, String userId) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        Subscriber sub = new Subscriber(emitter, startTime, endTime,
                userId == null || userId.isBlank() ? null : userId);
        subscribers.add(sub);
        emitter.onCompletion(() -> subscribers.remove(sub));
        emitter.onTimeout(() -> subscribers.remove(sub));
        emitter.onError(e -> subscribers.remove(sub));
        // 首帧立刻给：否则页面要空等一个 tick 才亮起来，像是没连上
        send(sub, snapshot(sub), "snapshot");
        return emitter;
    }

    /**
     * 标记用量已变化。
     *
     * <p>由写入侧（{@code SysAiUsageServiceImpl#record}）在事务提交后调用。
     * 刻意**不**在此处直接推送：写入方跑在业务线程上，一次 agent 任务可能连续
     * 写几十行，直接推会把业务线程拖进序列化与网络 IO。</p>
     */
    public void markDirty() {
        version.incrementAndGet();
    }

    /**
     * 定时合并推送。
     *
     * <p>{@code initialDelay} 故意大于 {@code fixedDelay}：应用刚启动时往往
     * 紧接着就是一次批量回填，那会儿推送只会白白刷屏。</p>
     */
    @Scheduled(initialDelay = 3000L, fixedDelay = flushIntervalMs)
    public void flush() {
        if (!scheduledEnabled || subscribers.isEmpty()) {
            return;
        }
        long current = version.get();
        if (current == lastFlushedVersion) {
            return;
        }
        lastFlushedVersion = current;
        // 同一时间窗的多个订阅者只查一次库
        Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
        for (Subscriber sub : subscribers) {
            String key = sub.startTime() + "|" + sub.endTime() + "|" + sub.userId();
            Map<String, Object> data = cache.computeIfAbsent(key, k -> snapshot(sub));
            send(sub, data, "update");
        }
    }

    /**
     * 上一次已推送的版本号。
     */
    private volatile long lastFlushedVersion = 0L;

    /**
     * 某时间窗的当前用量快照。
     *
     * @param sub 订阅者
     * @return 快照负载
     */
    private Map<String, Object> snapshot(Subscriber sub) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            SysAiUsageService.UsageStatistics s = usageService.getStatistics(
                    null, null, null, sub.startTime(), sub.endTime(), sub.userId());
            data.put("totalCalls", s.totalCalls == null ? 0L : s.totalCalls);
            data.put("totalTokens", s.totalTokens == null ? 0L : s.totalTokens);
            data.put("totalCost", s.totalCost == null ? 0D : s.totalCost);
            List<AiUsageModelTrendPointVO> today = usageService.getDailyModelTrend(
                    LocalDateTime.now().toLocalDate().atStartOfDay(), null, sub.userId());
            data.put("todayTokens", today.stream()
                    .mapToLong(p -> p.totalTokens() == null ? 0L : p.totalTokens())
                    .sum());
        } catch (Exception e) {
            // 单次统计失败不能让整条流断掉：下一拍还会再试
            log.debug("[UsageLive] 计算快照失败: {}", e.getMessage());
        }
        data.put("version", version.get());
        return data;
    }

    /**
     * 向单个订阅者推送一帧。
     *
     * @param sub  订阅者
     * @param data 负载
     * @param name 事件名
     */
    private void send(Subscriber sub, Map<String, Object> data, String name) {
        try {
            sub.emitter().send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            // 客户端已断开（关标签页/切路由）：顺手摘掉，避免列表无限堆积
            subscribers.remove(sub);
        }
    }

    /**
     * 当前订阅者数量（诊断用）。
     *
     * @return 订阅者数
     */
    public int subscriberCount() {
        return subscribers.size();
    }
}