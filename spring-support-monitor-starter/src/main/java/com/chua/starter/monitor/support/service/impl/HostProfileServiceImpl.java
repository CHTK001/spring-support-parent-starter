package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主机画像服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostProfileServiceImpl implements HostProfileService {

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    /**
     * agent HTTP 客户端
    */
    private final AgentHttpClient agentClient;

    @Override
    public ReturnResult<Map<String, Object>> refresh(Long hostId, boolean persist) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        Map<String, Object> info;
        Map<String, Object> metrics;
        try {
            info = agentClient.getMap(host, "/info");
            metrics = agentClient.getMap(host, "/metrics");
        } catch (Exception e) {
            String reason = cut(e.getMessage());
            if (persist) {
                markError(hostId, reason);
            }
            return ReturnResult.error("Agent 不可达: " + reason);
        }
        if (persist) {
            persistSnapshot(host, info, metrics);
        }
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("info", info);
        data.put("metrics", metrics);
        data.put("host", host);
        data.put("port", agentClient.portOf(host));
        data.put("osMismatch", osMismatch(host, info));
        data.put("collectedAt", LocalDateTime.now().toString());
        return ReturnResult.ok(data);
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> section(Long hostId, String section, Integer page, Integer size) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        String name = section == null ? "" : section.trim().toLowerCase(Locale.ROOT);
        String path = switch (name) {
            case SECTION_PORTS -> "/ports";
            case SECTION_PROCESSES -> "/processes";
            case SECTION_SERVICES -> "/services";
            case SECTION_DISKS -> "/disks";
            case SECTION_NETWORK -> "/network";
            case SECTION_ENV -> "/env";
            default -> null;
        };
        if (path == null) {
            return ReturnResult.error("不支持的画像分区: " + section);
        }
        Map<String, String> query = null;
        if (SECTION_PROCESSES.equals(name) || SECTION_SERVICES.equals(name)) {
            query = new LinkedHashMap<>(2);
            query.put("page", String.valueOf(page == null || page < 1 ? 1 : page));
            query.put("pageSize", String.valueOf(size == null || size < 1 ? 50 : Math.min(size, 500)));
        }
        try {
            List<Map<String, Object>> list = agentClient.getList(host, path, query);
            clearError(hostId);
            return ReturnResult.ok(list);
        } catch (Exception e) {
            markError(hostId, cut(e.getMessage()));
            return ReturnResult.error("Agent 不可达: " + cut(e.getMessage()));
        }
    }

    /**
     * 把 agent 回报的硬件与用量快照写回主机台账。
     *
     * @param host    主机
     * @param info    /info 负载
     * @param metrics /metrics 负载
     */
    private void persistSnapshot(MsServerHost host, Map<String, Object> info, Map<String, Object> metrics) {
        String osVersion = text(info.get("osVersion"));
        if (!osVersion.isEmpty()) {
            host.setServerHostOsVersion(osVersion);
        }
        String arch = text(info.get("arch"));
        if (!arch.isEmpty()) {
            host.setServerHostOsArch(arch);
        }
        Long cores = asLong(info.get("cpuCores"));
        if (cores != null && cores > 0) {
            host.setServerHostCpuCores(cores.intValue());
        }
        Long memoryMb = asLong(info.get("memoryMb"));
        if (memoryMb != null && memoryMb > 0) {
            host.setServerHostMemoryMb(memoryMb);
        }
        Double diskGb = asDouble(info.get("diskTotalGb"));
        if (diskGb != null && diskGb > 0) {
            host.setServerHostDiskGb((long) Math.round(diskGb));
        }
        String privateIp = text(info.get("privateIp"));
        if (!privateIp.isEmpty()) {
            host.setServerHostPrivateIp(privateIp);
        }
        String publicIp = text(info.get("publicIp"));
        if (!publicIp.isEmpty()) {
            host.setServerHostPublicIp(publicIp);
        }

        Double cpuPercent = asDouble(metrics.get("cpuPercent"));
        if (cpuPercent != null) {
            host.setServerHostCpuUsage(cpuPercent);
        }
        Double memPercent = asDouble(metrics.get("memoryPercent"));
        if (memPercent != null) {
            host.setServerHostMemUsage(memPercent);
        }
        Double diskPercent = asDouble(metrics.get("diskPercent"));
        if (diskPercent != null) {
            host.setServerHostDiskUsage(diskPercent);
        }

        LocalDateTime now = LocalDateTime.now();
        host.setServerHostOnline(1);
        host.setServerHostOnlineTime(now);
        host.setServerHostLastCollectTime(now);
        host.setServerHostAgentTime(now);
        host.setServerHostAgentInstalled(1);
        host.setServerHostAgentPort(agentClient.portOf(host));
        String version = text(info.get("agentVersion"));
        if (!version.isEmpty()) {
            host.setServerHostAgentVersion(version);
        }
        hostMapper.updateById(host);
        // 采集成功即清掉历史失败原因（updateById 会跳过 null 字段，必须走显式置空）
        clearError(host.getId());
    }

    /**
     * agent 回报的系统族与台账 osType 不一致时给出提示。
     * <p>
     * 不自动改写 osType：它决定走 SSH 还是 WinRM、绑定哪套凭据，静默改写会让下一次连接直接失败。
     * </p>
     *
     * @param host 主机
     * @param info /info 负载
     * @return 不一致时返回提示文案，一致时返回 null
     */
    private String osMismatch(MsServerHost host, Map<String, Object> info) {
        String reported = text(info.get("os")).toUpperCase(Locale.ROOT);
        String recorded = host.getServerHostOsType() == null ? "" : host.getServerHostOsType().toUpperCase(Locale.ROOT);
        if (reported.isEmpty() || recorded.isEmpty()) {
            return null;
        }
        boolean same = (reported.contains("WINDOWS") && recorded.contains("WIN"))
                || (!reported.contains("WINDOWS") && !recorded.contains("WIN"));
        return same ? null : "Agent 回报系统为 " + text(info.get("os")) + "，台账记录为 "
                + host.getServerHostOsType() + "，请确认远程通道配置";
    }

    /**
     * 记录 agent 访问失败原因。
     *
     * @param hostId 主机标识
     * @param reason 原因
     */
    private void markError(Long hostId, String reason) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return;
        }
        host.setServerHostAgentError(cut(reason));
        host.setServerHostAgentInstalled(0);
        hostMapper.updateById(host);
    }

    /**
     * 清除历史失败原因（必须显式置空：updateById 的字段策略会跳过 null）。
     *
     * @param hostId 主机标识
     */
    private void clearError(Long hostId) {
        if (hostId == null) {
            return;
        }
        hostMapper.update(null, new LambdaUpdateWrapper<MsServerHost>()
                .eq(MsServerHost::getId, hostId)
                .set(MsServerHost::getServerHostAgentError, null));
    }

    /**
     * 取文本。
     *
     * @param value 原值
     * @return 文本，null 转空串
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 取长整数。
     *
     * @param value 原值
     * @return 长整数，取不到返回 null
     */
    private Long asLong(Object value) {
        Double number = asDouble(value);
        return number == null ? null : (long) Math.round(number);
    }

    /**
     * 取小数。
     *
     * @param value 原值
     * @return 小数，取不到返回 null
     */
    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = text(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.replace("%", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截断过长的错误信息。
     *
     * @param text 原文
     * @return 截断结果
     */
    private String cut(String text) {
        if (text == null || text.isBlank()) {
            return "未知错误";
        }
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() > 480 ? one.substring(0, 480) : one;
    }
}
