package com.chua.starter.monitor.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.HostProfileVO;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
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
     * 主机映射器
     */
    @Autowired
    private MsServerHostMapper hostMapper;

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
        ReturnResult<HostProfileVO> refreshed = profileService.refresh(host.getId(), true);
        if (!refreshed.isOk()) {
            return ReturnResult.error(refreshed.getMsg());
        }
        MsServerHost latest = hostMapper.selectById(host.getId());
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
    private void markOnline(Long hostId, int online) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return;
        }
        host.setServerHostOnline(online);
        host.setServerHostOnlineTime(java.time.LocalDateTime.now());
        hostMapper.updateById(host);
    }
}
