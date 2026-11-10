// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.scheduler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.service.ReleaseService;
import com.chua.starter.server.support.sse.ReleaseSseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Actuator 定时采集调度器（全局时间轮驱动）。
 *
 * <p>项目的「Actuator 定时采集」开关存在 {@code ms_server_config}，由
 * {@link ServerTaskScheduler} 的全局时间轮以固定粒度扫描：所有项目共用同一个时间轮
 * 与同一条发布管理 SSE 长连接，不给每个项目单独起线程。</p>
 *
 * <p>采集动作本身是到目标主机执行一次端点探测（HTTP 走目标主机本地），
 * 可能耗时数秒，因此丢到虚拟线程执行，tick 本身只做「到期判断 + 占位」，
 * 不阻塞时间轮上的其它任务。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActuatorMonitorScheduler {

    /**
     * 发布服务（复用已有的端点探测，避免两套 SSH 命令）
     */
    private final ReleaseService releaseService;

    /**
     * 发布管理全局 SSE 通道（采集结果统一经它推送）
     */
    private final ReleaseSseHub sseHub;

    /**
     * 采集结果 JSON 序列化
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 虚拟线程执行器（采集到目标主机的 SSH 命令可能较慢，放后台执行）
     */
    private static final ExecutorService VIRTUAL_EXECUTOR =
            // [P3C C1 豁免] 场景化创建虚拟线程，需文档化
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("actuator-monitor-").factory());

    /**
     * 在途采集项目集合：同一项目的上一轮尚未结束时不重复发起
     */
    private static final Set<Long> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /**
     * 每个项目的下一次到期时刻（epoch 毫秒）
     */
    private static final Map<Long, Long> NEXT_AT = new ConcurrentHashMap<>();

    /**
     * 毫秒换算
     */
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * 配置缺省间隔（秒）：与后端保存时的默认值保持一致
     */
    private static final long DEFAULT_INTERVAL_SECONDS = 30L;

    /**
     * 扫描一次：把到期且开启采集的项目派发到后台执行。
     */
    public void tick() {
        try {
            List<Map<String, Object>> monitors = releaseService.listActuatorMonitors();
            if (monitors.isEmpty()) {
                NEXT_AT.clear();
                return;
            }
            long now = System.currentTimeMillis();
            Set<Long> configured = new HashSet<>();
            for (Map<String, Object> monitor : monitors) {
                Long projectId = toLong(monitor.get("projectId"));
                if (projectId == null) {
                    continue;
                }
                configured.add(projectId);
                Long seconds = toLong(monitor.get("intervalSeconds"));
                long intervalMs = (seconds == null ? DEFAULT_INTERVAL_SECONDS : seconds) * MILLIS_PER_SECOND;
                long next = NEXT_AT.getOrDefault(projectId, now);
                if (now < next) {
                    continue;
                }
                NEXT_AT.put(projectId, now + intervalMs);
                if (!IN_FLIGHT.add(projectId)) {
                    continue;
                }
                CompletableFuture.runAsync(() -> collect(projectId), VIRTUAL_EXECUTOR);
            }
            // 已关闭采集的项目从到期表里移除，重新开启时立即采集一次
            NEXT_AT.keySet().retainAll(configured);
        } catch (Exception e) {
            log.warn("[actuator-monitor] 扫描异常: {}", e.getMessage());
        }
    }

    /**
     * 采集一个项目并通过全局 SSE 广播结果。
     *
     * @param projectId 项目ID
     */
    private void collect(Long projectId) {
        try {
            ReturnResult<Map<String, Object>> result = releaseService.probeProjectEndpoints(projectId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", projectId);
            payload.put("at", System.currentTimeMillis());
            if (result != null && result.isOk() && result.getData() != null) {
                payload.putAll(result.getData());
            } else {
                payload.put("message", result == null ? "端点探测失败" : result.getMsg());
            }
            sseHub.broadcast(ReleaseSseHub.EVENT_PROJECT_ENDPOINTS, JSON.writeValueAsString(payload));
        } catch (Exception e) {
            log.debug("[actuator-monitor] 采集失败 projectId={}: {}", projectId, e.getMessage());
        } finally {
            IN_FLIGHT.remove(projectId);
        }
    }

    /**
     * 把配置里的数值转成 Long。
     *
     * @param raw 原始值
     * @return 数值；无法解析时返回 null
     */
    private Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
