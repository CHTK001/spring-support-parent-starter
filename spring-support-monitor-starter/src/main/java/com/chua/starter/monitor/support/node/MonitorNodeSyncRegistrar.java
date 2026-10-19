package com.chua.starter.monitor.support.node;

import com.chua.starter.sync.support.server.SyncServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 把 {@code monitor/app/report} 处理器挂到 sync 服务端。
 *
 * <p>在 {@link ApplicationReadyEvent} 阶段注册，确保 sync 服务端实例已经启动；
 * 未启用 sync 服务端时只记录告警，节点列表保持为空，不影响应用启动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorNodeSyncRegistrar {

    private final ObjectProvider<SyncServer> syncServerProvider;
    private final MonitorNodeRegistry registry;

    /**
     * 注册应用信息上报处理器。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        SyncServer server = syncServerProvider.getIfAvailable();
        if (server == null) {
            log.warn("[MonitorNode] 未启用 sync 服务端，在线节点将为空；请检查 plugin.sync.type=server 且 plugin.sync.server.enable=true");
            return;
        }
        server.registerHandler(MonitorNodeRegistry.TOPIC_APP_REPORT, new MonitorNodeSyncHandler(registry));
        log.info("[MonitorNode] 已注册 report 客户端节点采集: topic={}", MonitorNodeRegistry.TOPIC_APP_REPORT);
    }
}
