package com.chua.starter.monitor.support.scheduler;

import com.chua.starter.monitor.support.context.MonitorAgentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 探针离线检测任务
 * <p>
 * 周期扫描探针心跳上下文，将超时未上报的目标翻转离线状态并告警日志；
 * 状态可通过 {@code GET /ms/monitor/agent/status} 查询。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOfflineChecker {

    /** 探针上下文 */
    private final MonitorAgentContext agentContext;

    /**
     * 每分钟执行一次离线扫描
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 90_000L)
    public void check() {
        List<Long> offlineIds = agentContext.sweepOffline();
        for (Long monitorId : offlineIds) {
            // 离线属于运维关注事件, 以 WARN 级别暴露; 后续可挂接通知渠道
            log.warn("[monitor] 探针离线 monitorId={}", monitorId);
        }
    }
}
