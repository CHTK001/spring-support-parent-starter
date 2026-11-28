package com.chua.starter.server.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.HostProfileVO;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.AgentHttpClient;
import com.chua.starter.server.support.service.HostProfileService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.chua.starter.server.support.spi.HostPushStateQuery;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 通道硬件探测提供者。
 *
 * <p>装了 monitor-agent 的主机由 Agent 自报硬件与用量；纯推送主机只查询
 * monitor 提供的在线态契约，不访问 monitor 的 Mapper 或上下文。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "agent", order = 100)
public class AgentHostDetectProvider implements HostDetectProvider {

    @Autowired
    private HostProfileService profileService;

    @Autowired
    private AgentHttpClient agentClient;

    @Autowired
    private ServerHostService serverHostService;

    @Autowired(required = false)
    private HostPushStateQuery pushStateQuery;

    @Override
    public String osType() {
        return "AGENT";
    }

    @Override
    public boolean requiresCredential() {
        return false;
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        if (isPushOnlyOnline(host)) {
            markOnline(host.getId(), 1);
            MsServerHost latest = serverHostService.findById(host.getId());
            return ReturnResult.ok(latest == null ? host : latest);
        }
        ReturnResult<HostProfileVO> refreshed = profileService.refresh(host.getId(), true);
        if (!refreshed.isOk()) {
            return ReturnResult.error(refreshed.getMsg());
        }
        MsServerHost latest = serverHostService.findById(host.getId());
        return ReturnResult.ok(latest == null ? host : latest);
    }

    @Override
    public ReturnResult<String> test(MsServerHost host) {
        if (isPushOnlyOnline(host)) {
            markOnline(host.getId(), 1);
            return ReturnResult.ok("探针心跳在线", "测试成功");
        }
        try {
            JsonNode health = agentClient.get(host, "/health", null);
            String version = health.path("version").asText("");
            markOnline(host.getId(), 1);
            return ReturnResult.ok("Agent v" + version + " 在线", "测试成功");
        } catch (Exception e) {
            markOnline(host.getId(), 0);
            log.warn("[detect] Agent 连通性测试失败 host={}: {}", host.getId(), e.getMessage());
            return ReturnResult.error("Agent 不可达: " + e.getMessage());
        }
    }

    private boolean isPushOnlyOnline(MsServerHost host) {
        return host != null && pushStateQuery != null
                && pushStateQuery.isPushOnline(host.getServerHostAgentKey(), host.getId())
                && !isAgentInstalled(host);
    }

    private boolean isAgentInstalled(MsServerHost host) {
        Integer installed = host == null ? null : host.getServerHostAgentInstalled();
        return installed != null && installed == 1;
    }

    private void markOnline(Long hostId, int online) {
        MsServerHost host = new MsServerHost();
        host.setId(hostId);
        host.setServerHostOnline(online);
        host.setServerHostOnlineTime(java.time.LocalDateTime.now());
        serverHostService.saveCollectState(host);
    }
}
