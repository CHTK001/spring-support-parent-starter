package com.chua.starter.monitor.support.provider.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.context.MonitorAgentContext;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.monitor.support.model.HostProfileVO;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 通道硬件探测提供者。
 *
 * <p>装了 monitor-agent 的主机由 Agent 自报硬件与用量，平台侧不需要 SSH/WinRM 登录身份；
 * 探测即刷新画像并回写主机台账，测连通即请求 Agent 的 /health。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "agent", order = 100)
public class AgentHostDetectProvider implements HostDetectProvider {

    /**
     * 主机画像服务
     */
    @Autowired
    private HostProfileService profileService;

    /**
     * agent HTTP 客户端
     */
    @Autowired
    private AgentHttpClient agentClient;

    /**
     * 主机台账门面（查询与采集状态回写）
     */
    @Autowired
    private ServerHostService serverHostService;

    /**
     * 监控目标 映射器（按探针标识/主机归属反查推送目标）
     */
    @Autowired
    private MsMonitorMapper monitorMapper;

    /**
     * 探针上下文（推送心跳在线判定）
     */
    @Autowired
    private MonitorAgentContext agentContext;

    /**
     * 系统类型 AGENT。
     *
     * @return AGENT
     */
    @Override
    public String osType() {
        return "AGENT";
    }

    /**
     * Agent 自报信息，无需登录凭据。
     *
     * @return false
     */
    @Override
    public boolean requiresCredential() {
        return false;
    }

    /**
     * 刷新画像并回写主机台账。
     *
     * @param host       目标主机
     * @param credential 恒为 null
     * @return 探测后的最新主机
     */
    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        if (isPushOnlyOnline(host)) {
            // 纯推送主机：心跳即活体，画像由推送指标承载，不走 Agent 拉取
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

    /**
     * 请求 Agent /health 判断通道连通。
     *
     * @param host 目标主机
     * @return 连通结果，成功时带上 Agent 版本
     */
    @Override
    public ReturnResult<String> test(MsServerHost host) {
        if (isPushOnlyOnline(host)) {
            // 纯推送主机：推送心跳在线即连通，无需探测 Agent 端口
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

    /**
     * 回写在线标记。
     *
     * @param hostId 主机标识
     * @param online 1 在线，0 离线
     */
    /**
     * 判定主机是否为「推送在线且未装独立 Agent」的纯推送主机。
     * <p>按主机台账探针标识、主机归属两条路径反查启用中的监控目标，
     * 命中且心跳在线才认定为推送活体；查不到或心跳超时均返回 {@code false}，
     * 走原有 Agent 拉取探测。</p>
     *
     * @param host 目标主机
     * @return true 表示推送心跳在线且无独立 Agent
     */
    private boolean isPushOnlyOnline(MsServerHost host) {
        MsMonitor monitor = pushOnlineMonitor(host);
        return monitor != null && !isAgentInstalled(host);
    }

    /**
     * 查找主机对应的推送监控目标，且其推送心跳在线。
     * <p>先按主机台账探针标识精确匹配，未配置时按 {@code ms_monitor.ms_server_host_id}
     * 归属反查；两条路径都要求监控目标处于启用状态。</p>
     *
     * @param host 目标主机
     * @return 在线的推送监控目标，未命中返回 {@code null}
     */
    private MsMonitor pushOnlineMonitor(MsServerHost host) {
        if (host == null || host.getId() == null) {
            return null;
        }
        MsMonitor monitor = null;
        if (host.getServerHostAgentKey() != null && !host.getServerHostAgentKey().isBlank()) {
            monitor = monitorMapper.selectOne(new LambdaQueryWrapper<MsMonitor>()
                    .eq(MsMonitor::getMonitorAgentKey, host.getServerHostAgentKey().trim())
                    .eq(MsMonitor::getMonitorEnabled, 1)
                    .last("LIMIT 1"));
        }
        if (monitor == null) {
            monitor = monitorMapper.selectOne(new LambdaQueryWrapper<MsMonitor>()
                    .eq(MsMonitor::getMsServerHostId, host.getId())
                    .eq(MsMonitor::getMonitorEnabled, 1)
                    .isNotNull(MsMonitor::getMonitorAgentKey)
                    .ne(MsMonitor::getMonitorAgentKey, "")
                    .last("LIMIT 1"));
        }
        return monitor != null && agentContext.isOnline(monitor.getId()) ? monitor : null;
    }

    /**
     * 主机是否已安装独立 Agent（拉取通道可用）。
     *
     * @param host 目标主机
     * @return true 表示已安装
     */
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
