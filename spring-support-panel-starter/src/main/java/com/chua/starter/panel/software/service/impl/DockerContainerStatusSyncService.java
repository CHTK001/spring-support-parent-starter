package com.chua.starter.panel.software.service.impl;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareBindingService;
import com.chua.starter.panel.service.DockerPanelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Docker 容器状态定时同步服务。
 * <p>
 * 定时检查业务表（sys_project_container_software_binding）中的 Docker 记录
 * 与实际 Docker 容器是否一致。若业务表标记为 SUCCESS/RUNNING 但容器实际不存在，
 * 自动将状态更新为 CONTAINER_MISSING/UNKNOWN。
 *
 * @author CH
 */
@Component
public class DockerContainerStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(DockerContainerStatusSyncService.class);

    @Value("${docker.api.host:}")
    private String dockerApiHost;

    @Value("${docker.api.port:2375}")
    private int dockerApiPort;

    private final SysProjectContainerSoftwareBindingService bindingService;
    private final DockerPanelService dockerPanelService;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;

    public DockerContainerStatusSyncService(
            SysProjectContainerSoftwareBindingService bindingService,
            DockerPanelService dockerPanelService,
            ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.bindingService = bindingService;
        this.dockerPanelService = dockerPanelService;
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    /**
     * 定时同步（默认每 5 分钟执行一次）。<br>
     * 仅当配置了 docker.api.host 时生效。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void scheduledSync() {
        if (!StringUtils.hasText(dockerApiHost)) {
            return;
        }
        log.info("[ContainerSync] 开始定时同步容器状态");
        try {
            String connectionId = dockerPanelService.open(dockerApiHost, dockerApiPort, "tcp");
            try {
                SysProjectContainerSoftwareBindingService.SyncSummary summary =
                        bindingService.syncContainerStatus(connectionId).getData();
                if (summary != null && summary.missingContainers() > 0) {
                    log.warn("[ContainerSync] 同步完成: 共检查 {} 条, 更新 {} 条, 发现 {} 个缺失容器",
                            summary.totalChecked(), summary.updated(), summary.missingContainers());
                    notifyMissingContainers(summary.missingContainers());
                } else if (summary != null) {
                    log.info("[ContainerSync] 同步完成: 共检查 {} 条, 状态均正常", summary.totalChecked());
                }
            } finally {
                dockerPanelService.close(connectionId);
            }
        } catch (Exception e) {
            log.error("[ContainerSync] 定时同步异常: {}", e.getMessage());
        }
    }

    /**
     * 当发现缺失容器时，通过 Socket.IO 推送通知
     */
    private void notifyMissingContainers(int count) {
        SocketSessionTemplate template = socketSessionTemplateProvider.getIfAvailable();
        if (template == null) return;

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("module", "PROJECT");
        envelope.put("event", "CONTAINER_SYNC_ALERT");
        envelope.put("data", Map.of(
                "type", "CONTAINER_MISSING",
                "count", count,
                "message", "发现 " + count + " 个容器不存在，已标记为 CONTAINER_MISSING",
                "timestamp", System.currentTimeMillis()
        ));
        template.broadcastObject("message", envelope);
    }
}
