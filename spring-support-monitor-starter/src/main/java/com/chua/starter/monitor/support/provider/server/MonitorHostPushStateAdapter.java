package com.chua.starter.monitor.support.provider.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.monitor.support.context.MonitorAgentContext;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.server.support.spi.HostPushStateQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * monitor 侧的主机推送在线态适配。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Component
@RequiredArgsConstructor
public class MonitorHostPushStateAdapter implements HostPushStateQuery {

    private final MsMonitorMapper monitorMapper;
    private final MonitorAgentContext agentContext;

    @Override
    public boolean isPushOnline(String agentKey, Long hostId) {
        MsMonitor monitor = null;
        if (agentKey != null && !agentKey.isBlank()) {
            monitor = monitorMapper.selectOne(new LambdaQueryWrapper<MsMonitor>()
                    .eq(MsMonitor::getMonitorAgentKey, agentKey.trim())
                    .eq(MsMonitor::getMonitorEnabled, 1)
                    .last("LIMIT 1"));
        }
        if (monitor == null && hostId != null) {
            monitor = monitorMapper.selectOne(new LambdaQueryWrapper<MsMonitor>()
                    .eq(MsMonitor::getMsServerHostId, hostId)
                    .eq(MsMonitor::getMonitorEnabled, 1)
                    .isNotNull(MsMonitor::getMonitorAgentKey)
                    .ne(MsMonitor::getMonitorAgentKey, "")
                    .last("LIMIT 1"));
        }
        return monitor != null && agentContext.isOnline(monitor.getId());
    }
}
