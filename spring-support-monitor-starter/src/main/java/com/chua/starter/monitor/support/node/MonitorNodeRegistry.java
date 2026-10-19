package com.chua.starter.monitor.support.node;

import com.chua.starter.sync.support.pojo.ClientInfo;
import com.chua.starter.sync.support.server.SyncServer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线节点注册表。
 *
 * <p>数据来源是 monitor 的 report 客户端注册：
 * <ul>
 *     <li>{@code sync/client/register}：{@link SyncServer} 内登记的客户端（IP / 端口 / 应用名）；</li>
 *     <li>{@code monitor/app/report}：{@code AppRegisterReporter} 每 30s 上报的应用信息
 *     （服务地址、JVM、OS、启动时间等）。</li>
 * </ul>
 * 两者按会话标识（{@code __syncClientId__}）合并，连接仍在即为在线。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@RequiredArgsConstructor
public class MonitorNodeRegistry {

    /**
     * 应用信息上报 topic（与 report-client 的 MonitorTopics.APP_REPORT 一致）
     */
    public static final String TOPIC_APP_REPORT = "monitor/app/report";

    /**
     * 服务端分发业务消息时注入的源客户端标识
     */
    public static final String SYNC_SOURCE_CLIENT_ID = "__syncClientId__";

    /**
     * 上报客户端注册服务端；未启用时为空
     */
    private final ObjectProvider<SyncServer> syncServerProvider;

    /**
     * 应用信息上报缓存：会话标识 -> 上报内容
     */
    private final Map<String, Map<String, Object>> appReports = new ConcurrentHashMap<>();

    /**
     * 接收一条应用信息上报。
     *
     * @param data 上报内容（服务端已注入 {@code __syncClientId__}）
     */
    public void onAppReport(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        String clientId = asString(data.get(SYNC_SOURCE_CLIENT_ID));
        if (!StringUtils.hasText(clientId)) {
            clientId = asString(data.get("clientId"));
        }
        if (!StringUtils.hasText(clientId)) {
            clientId = reportKey(data);
        }
        appReports.put(clientId, new LinkedHashMap<>(data));
    }

    /**
     * 全部已注册节点（含离线）。
     *
     * @return 节点列表
     */
    public List<MonitorNodeVO> list() {
        SyncServer server = syncServerProvider.getIfAvailable();
        if (server == null) {
            return appReports.entrySet().stream()
                    .map(e -> fromReport(e.getKey(), e.getValue(), false))
                    .toList();
        }
        Map<String, ClientInfo> clients = server.getAllClientsRaw();
        Set<String> connected = server.getConnectedSessions();
        List<MonitorNodeVO> result = new ArrayList<>(clients.size());
        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            String sessionId = entry.getKey();
            ClientInfo info = entry.getValue();
            boolean online = connected.contains(sessionId) || (info != null && info.isClientOnline());
            result.add(toVO(sessionId, info, appReports.get(sessionId), online));
        }
        // 只收到 app/report、没有 client 注册记录的客户端也纳入（注册消息可能丢失或协议不同）
        for (Map.Entry<String, Map<String, Object>> entry : appReports.entrySet()) {
            boolean exists = result.stream().anyMatch(n -> Objects.equals(n.clientId(), entry.getKey()));
            if (!exists) {
                result.add(fromReport(entry.getKey(), entry.getValue(), false));
            }
        }
        return result;
    }

    /**
     * 仅在线节点。
     *
     * @return 在线节点列表
     */
    public List<MonitorNodeVO> online() {
        return list().stream().filter(n -> Boolean.TRUE.equals(n.online())).toList();
    }

    /**
     * 节点统计。
     *
     * @return 统计结果
     */
    public MonitorNodeStatsVO stats() {
        List<MonitorNodeVO> all = list();
        long online = all.stream().filter(n -> Boolean.TRUE.equals(n.online())).count();
        return new MonitorNodeStatsVO(all.size(), online, all.size() - online);
    }

    /**
     * 组装节点视图：注册信息为主，应用上报为补充。
     *
     * @param sessionId 会话标识
     * @param info      注册信息，可为 空
     * @param report    应用上报，可为 空
     * @param online    是否在线
     * @return 节点视图
     */
    private MonitorNodeVO toVO(String sessionId, ClientInfo info, Map<String, Object> report, boolean online) {
        String applicationName = firstText(report, "applicationName",
                info == null ? null : info.getClientApplicationName());
        String ipAddress = firstText(report, "ipAddress", info == null ? null : info.getClientIpAddress());
        Integer port = firstInt(report, "serverPort",
                info == null || info.getClientPort() <= 0 ? null : info.getClientPort());
        String hostname = firstText(report, "hostname", info == null ? null : info.getClientHostname());
        String contextPath = firstText(report, "contextPath", info == null ? null : info.getClientContextPath());
        String serviceUrl = firstText(report, "serviceUrl", info == null ? null : info.getClientUrl());
        String osName = firstText(report, "osName", info == null ? null : info.getClientOsName());
        String javaVersion = firstText(report, "javaVersion", info == null ? null : info.getClientJavaVersion());
        Long pid = firstLong(report, "pid", info == null || info.getClientPid() <= 0 ? null : info.getClientPid());
        Long startTime = firstLong(report, "startTime",
                info == null || info.getClientStartTime() <= 0 ? null : info.getClientStartTime());
        Long registerTime = info != null && info.getClientRegisterTime() > 0
                ? info.getClientRegisterTime()
                : firstLong(report, "startTime", null);
        Long lastHeartbeat = info != null && info.getClientLastHeartbeatTime() > 0
                ? info.getClientLastHeartbeatTime()
                : firstLong(report, "timestamp", null);
        Long uptime = firstLong(report, "uptime",
                info == null || info.getClientUptime() <= 0 ? null : info.getClientUptime());
        String activeProfiles = firstText(report, "activeProfiles", null);

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (info != null && info.getClientMetadata() != null) {
            metadata.putAll(info.getClientMetadata());
        }
        for (String key : List.of("jvmName", "jvmVersion", "osVersion", "osArch", "heapUsed", "heapMax", "threadCount")) {
            if (report != null && report.get(key) != null) {
                metadata.put(key, report.get(key));
            }
        }
        return new MonitorNodeVO(sessionId, applicationName, activeProfiles, hostname, ipAddress, port,
                contextPath, serviceUrl, osName, javaVersion, pid, startTime, registerTime, lastHeartbeat,
                uptime, online, metadata);
    }

    /**
     * 仅凭应用上报组装节点视图（无注册记录时使用）。
     *
     * @param clientId 会话标识
     * @param report   应用上报
     * @param online   是否在线
     * @return 节点视图
     */
    private MonitorNodeVO fromReport(String clientId, Map<String, Object> report, boolean online) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String key : List.of("jvmName", "jvmVersion", "osVersion", "osArch", "heapUsed", "heapMax", "threadCount")) {
            if (report != null && report.get(key) != null) {
                metadata.put(key, report.get(key));
            }
        }
        return new MonitorNodeVO(clientId,
                firstText(report, "applicationName", null),
                firstText(report, "activeProfiles", null),
                firstText(report, "hostname", null),
                firstText(report, "ipAddress", null),
                firstInt(report, "serverPort", null),
                firstText(report, "contextPath", null),
                firstText(report, "serviceUrl", null),
                firstText(report, "osName", null),
                firstText(report, "javaVersion", null),
                firstLong(report, "pid", null),
                firstLong(report, "startTime", null),
                firstLong(report, "startTime", null),
                firstLong(report, "timestamp", null),
                firstLong(report, "uptime", null),
                online,
                metadata);
    }

    /**
     * 上报内容去重键：应用名 + IP + 端口。
     *
     * @param report 上报内容
     * @return 去重键
     */
    private static String reportKey(Map<String, Object> report) {
        return asString(report.get("applicationName")) + "|" + asString(report.get("ipAddress"))
                + "|" + asString(report.get("serverPort"));
    }

    /**
     * 转字符串（null 安全）。
     *
     * @param value 原值
     * @return 字符串或 null
     */
    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 取首个非空文本。
     *
     * @param report   上报内容，可为 空
     * @param key      键
     * @param fallback 兜底值
     * @return 文本
     */
    private static String firstText(Map<String, Object> report, String key, String fallback) {
        String value = report == null ? null : asString(report.get(key));
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 取首个可用整数。
     *
     * @param report   上报内容，可为 空
     * @param key      键
     * @param fallback 兜底值
     * @return 整数
     */
    private static Integer firstInt(Map<String, Object> report, String key, Integer fallback) {
        Object value = report == null ? null : report.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 取首个可用长整数。
     *
     * @param report   上报内容，可为 空
     * @param key      键
     * @param fallback 兜底值
     * @return 长整数
     */
    private static Long firstLong(Map<String, Object> report, String key, Long fallback) {
        Object value = report == null ? null : report.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
