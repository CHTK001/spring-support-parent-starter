// [P3C A20 豁免] A20/A30/A31: 含日志/事件名/JSON 字段等非业务魔法值场景，豁免。
// [P3C C1 豁免] C1: 使用命名守护线程+专用线程池（非自行 new Thread 阻塞拓扑），实际符合 P3C 推荐实践，扫描器按标记豁免。
package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.HostResourceSummaryVO;
import com.chua.starter.server.support.sse.HostAssetsSseHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主机资源摘要 SSE 推送（句柄数 / inode 使用率）
 *
 * <p>底部状态条原先每 30 秒 GET 一次
 * {@code /ms/server/host/profile/{hostId}/summary}，现在改为服务端推送：
 * 前端在主机资产页建立唯一 SSE 连接，选中主机时调用订阅接口，服务端在
 * 订阅后立即采一次、之后每 30 秒采一次，经 {@link HostAssetsSseHub} 以
 * {@code host-summary} 事件下发。同一台主机被多个客户端关注时只采集一次。</p>
 *
 * <p>采集走主机现有采集通道，可能耗时数秒，因此订阅时的首推放在独立线程执行，
 * 不阻塞订阅接口；采集失败时下发一份各字段为 -1 的负载，前端显示「—」，
 * 与「这台机器句柄为 0」区分开。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostSummarySseService {

    /**
     * 摘要推送事件名
     */
    private static final String EVENT_HOST_SUMMARY = "host-summary";

    /**
     * 定时推送间隔（毫秒），与原先前端轮询间隔保持一致
     */
    private static final long PUSH_INTERVAL_MS = 30_000L;

    /**
     * 取不到值时的占位（前端按 -1 显示「—」）
     */
    private static final long UNKNOWN = -1L;

    /**
     * SSE 通道
     */
    private final HostAssetsSseHub sseHub;

    /**
     * 主机画像服务（摘要采集入口）
     */
    private final HostProfileService profileService;

    /**
     * JSON 序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 客户端标识 → 关注的主机标识
     */
    private final Map<String, Long> subscriptions = new ConcurrentHashMap<>();

    /**
     * 正在采集中的主机（避免慢链路下同一台主机的采集任务堆叠）
     */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 首推线程：单线程串行采集，避免同时订阅多台主机时把采集通道打满
     */
    private final ExecutorService pushExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "host-summary-sse-push");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 订阅某台主机的资源摘要。
     *
     * @param clientId 客户端标识
     * @param hostId   主机标识
     */
    public void subscribe(String clientId, Long hostId) {
        if (clientId == null || clientId.isBlank() || hostId == null) {
            return;
        }
        subscriptions.put(clientId, hostId);
        pushExecutor.execute(() -> pushIfStillSubscribed(clientId, hostId));
    }

    /**
     * 取消该客户端的摘要订阅。
     *
     * @param clientId 客户端标识
     */
    public void unsubscribe(String clientId) {
        if (clientId != null) {
            subscriptions.remove(clientId);
        }
    }

    /**
     * 周期推送：每轮按主机去重，采集放到独立线程，避免慢链路（SSH/WinRM）把调度线程占住。
     * <p>同一台主机上一轮还没采完时跳过本轮，避免任务堆叠；同时清理已断开客户端留下的订阅。</p>
     */
    @Scheduled(fixedDelay = PUSH_INTERVAL_MS)
    public void pushTick() {
        if (subscriptions.isEmpty()) {
            return;
        }
        Map<Long, List<String>> byHost = new HashMap<>(subscriptions.size());
        for (Map.Entry<String, Long> entry : subscriptions.entrySet()) {
            if (!sseHub.isConnected(entry.getKey())) {
                subscriptions.remove(entry.getKey());
                continue;
            }
            byHost.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        for (Map.Entry<Long, List<String>> entry : byHost.entrySet()) {
            Long hostId = entry.getKey();
            List<String> clientIds = entry.getValue();
            if (!inFlight.add(hostId)) {
                continue;
            }
            pushExecutor.execute(() -> {
                try {
                    String payload = collectPayload(hostId);
                    for (String clientId : clientIds) {
                        sseHub.send(clientId, EVENT_HOST_SUMMARY, payload);
                    }
                } finally {
                    inFlight.remove(hostId);
                }
            });
        }
    }

    /**
     * 停服时释放首推线程。
     */
    @PreDestroy
    public void shutdown() {
        pushExecutor.shutdownNow();
    }

    /**
     * 首推前再确认一次订阅仍然有效（用户可能在采集期间切走了别的机器）。
     *
     * @param clientId 客户端标识
     * @param hostId   主机标识
     */
    private void pushIfStillSubscribed(String clientId, Long hostId) {
        if (!hostId.equals(subscriptions.get(clientId)) || !sseHub.isConnected(clientId)) {
            return;
        }
        sseHub.send(clientId, EVENT_HOST_SUMMARY, collectPayload(hostId));
    }

    /**
     * 采集并序列化一台主机的资源摘要。
     *
     * @param hostId 主机标识
     * @return JSON 文本（失败时为全 -1 负载）
     */
    private String collectPayload(Long hostId) {
        HostResourceSummaryVO summary = null;
        try {
            ReturnResult<HostResourceSummaryVO> result = profileService.summary(hostId);
            if (result != null && result.isOk()) {
                summary = result.getData();
            }
        } catch (Exception e) {
            log.debug("[host-summary-sse] 摘要采集失败 hostId={}: {}", hostId, e.getMessage());
        }
        Map<String, Object> payload = new LinkedHashMap<>(8);
        payload.put("hostId", hostId);
        payload.put("handleCount", summary == null ? UNKNOWN : summary.handleCount());
        payload.put("inodeUsed", summary == null ? UNKNOWN : summary.inodeUsed());
        payload.put("inodeTotal", summary == null ? UNKNOWN : summary.inodeTotal());
        payload.put("inodePercent", summary == null ? UNKNOWN : summary.inodePercent());
        payload.put("inodeMount", summary == null || summary.inodeMount() == null ? "" : summary.inodeMount());
        payload.put("collectedAt", summary == null || summary.collectedAt() == null ? "" : summary.collectedAt());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.debug("[host-summary-sse] 摘要序列化失败 hostId={}: {}", hostId, e.getMessage());
            return "{\"hostId\":" + hostId + "}";
        }
    }
}
