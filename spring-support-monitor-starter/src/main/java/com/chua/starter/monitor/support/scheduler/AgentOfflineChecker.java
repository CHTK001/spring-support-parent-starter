package com.chua.starter.monitor.support.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.monitor.support.context.MonitorAgentContext;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.service.ServerHostService;
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
 * 翻转离线时同步回写主机台账在线状态，使资产视图与推送通道状态一致。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOfflineChecker {

    /**
     * 探针上下文
     */
    private final MonitorAgentContext agentContext;

    /**
     * 监控目标 映射器
     */
    private final MsMonitorMapper monitorMapper;

    /**
     * 主机台账门面（离线联动回写主机在线状态）
     */
    private final ServerHostService serverHostService;

    /**
     * 每分钟执行一次离线扫描
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 90_000L)
    public void check() {
        List<Long> offlineIds = agentContext.sweepOffline();
        for (Long monitorId : offlineIds) {
            // 离线属于运维关注事件, 以 WARN 级别暴露; 后续可挂接通知渠道
            log.warn("[monitor] 探针离线 monitorId={}", monitorId);
            markHostOffline(monitorId);
        }
    }

    /**
     * 探针翻转离线时回写主机台账离线（只影响上报过心跳的推送目标）。
     * <p>归属优先取监控资产已绑定的主机标识，未绑定时按探针标识回查主机台账；
     * 监控资产或主机已删除时静默跳过。</p>
     *
     * @param monitorId 监控目标标识
     */
    private void markHostOffline(Long monitorId) {
        try {
            MsMonitor monitor = monitorMapper.selectById(monitorId);
            if (monitor == null) {
                return;
            }
            MsServerHost host = null;
            if (monitor.getMsServerHostId() != null) {
                host = serverHostService.findById(monitor.getMsServerHostId());
            }
            if (host == null && monitor.getMonitorAgentKey() != null && !monitor.getMonitorAgentKey().isBlank()) {
                host = serverHostService.findByAgentKey(monitor.getMonitorAgentKey().trim());
            }
            if (host == null) {
                return;
            }
            MsServerHost state = new MsServerHost();
            state.setId(host.getId());
            state.setServerHostOnline(0);
            state.setServerHostOnlineTime(java.time.LocalDateTime.now());
            serverHostService.saveCollectState(state);
        } catch (Exception e) {
            log.warn("[monitor] 探针离线回写主机状态失败 monitorId={}: {}", monitorId, e.getMessage());
        }
    }
}
