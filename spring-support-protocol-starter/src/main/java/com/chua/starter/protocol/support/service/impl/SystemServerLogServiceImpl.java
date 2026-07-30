package com.chua.starter.protocol.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.network.protocol.filter.FirewallServletFilter;
import com.chua.common.support.network.protocol.firewall.FirewallAction;
import com.chua.common.support.network.protocol.firewall.FirewallEvent;
import com.chua.common.support.network.protocol.firewall.FirewallEventQuery;
import com.chua.common.support.network.protocol.firewall.FirewallMetrics;
import com.chua.common.support.network.protocol.firewall.FirewallMetricsQuery;
import com.chua.common.support.network.protocol.firewall.FirewallStorageSpi;
import com.chua.common.support.network.protocol.firewall.MemoryFirewallStorageSpi;
import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.starter.protocol.support.entity.SystemServerLog;
import com.chua.starter.protocol.support.mapper.SystemServerLogMapper;
import com.chua.starter.protocol.support.service.server.SystemServerService;
import com.chua.starter.protocol.support.service.server.SystemServerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 系统服务器日志服务实现类
 *
 * @author CH
 * @since 2023-04-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerLogServiceImpl extends ServiceImpl<SystemServerLogMapper, SystemServerLog> implements SystemServerLogService {
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_BASE_DIR = "logs/system-server";

    private final SystemServerService systemServerService;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, Math.max(4, Runtime.getRuntime().availableProcessors()),
            60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10000),
            r -> new Thread(r, "systemserverLog-recorder"),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    /**
     * 分页查询系统服务器日志
     *
     * @param page         分页参数，例如：new Page<>(1, 10)
     * @param serverId     服务器ID，用于筛选特定服务器的日志，例如：1
     * @param filterType   过滤器类型，例如："com.example.MyFilter"
     * @param processStatus 处理状态，例如："BLACKLIST_BLOCK"
     * @param clientIp     客户端IP地址，例如："192.168.1.100"
     * @param startTime    查询开始时间，例如：LocalDateTime.of(2023, 1, 1, 0, 0, 0)
     * @param endTime      查询结束时间，例如：LocalDateTime.now()
     * @return 分页查询结果
     */
    @Override
    public ReturnResult<IPage<SystemServerLog>> pageLogs(Page<SystemServerLog> page, Integer serverId, String filterType, String processStatus, String clientIp, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<SystemServerLog> records = readLogsFromFile(serverId, filterType, processStatus, clientIp, startTime, endTime);
            long current = page.getCurrent() <= 0 ? 1 : page.getCurrent();
            long size = page.getSize() <= 0 ? 20 : page.getSize();
            int from = (int) ((current - 1) * size);
            int to = Math.min(records.size(), from + (int) size);
            List<SystemServerLog> paged = from >= records.size() ? List.of() : records.subList(from, to);
            page.setTotal(records.size());
            page.setRecords(paged);
            return ReturnResult.success(page);
        } catch (Exception e) {
            log.error("分页查询systemserverLog失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 导出系统服务器日志为CSV格式
     *
     * @param serverId     服务器ID，用于筛选特定服务器的日志，例如：1
     * @param filterType   过滤器类型，例如："com.example.MyFilter"
     * @param processStatus 处理状态，例如："BLACKLIST_BLOCK"
     * @param clientIp     客户端IP地址，例如："192.168.1.100"
     * @param startTime    查询开始时间，例如：LocalDateTime.of(2023, 1, 1, 0, 0, 0)
     * @param endTime      查询结束时间，例如：LocalDateTime.now()
     * @return CSV格式的字节数组
     */
    @Override
    public ReturnResult<byte[]> exportCsv(Integer serverId, String filterType, String processStatus, String clientIp, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<SystemServerLog> list = readLogsFromFile(serverId, filterType, processStatus, clientIp, startTime, endTime);
            String header = "store_time,access_time,server_id,filter_type,rule_category,action_status,process_status,method,path,host,referer,request_id,client_ip,client_geo,user_agent,reason,status_code,duration_ms\n";
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String body = list.stream().map(it -> String.join(",",
                    fmtSafe(it.getStoreTime(), fmt),
                    fmtSafe(it.getAccessTime(), fmt),
                    safe(String.valueOf(it.getServerId() == null ? "" : it.getServerId())),
                    safe(it.getFilterType()),
                    safe(it.getRuleCategory()),
                    safe(it.getActionStatus()),
                    safe(it.getProcessStatus()),
                    safe(it.getMethod()),
                    safe(it.getPath()),
                    safe(it.getHost()),
                    safe(it.getReferer()),
                    safe(it.getRequestId()),
                    safe(it.getClientIp()),
                    safe(it.getClientGeo()),
                    safe(it.getUserAgent()),
                    safe(it.getDecisionReason()),
                    safe(String.valueOf(it.getStatusCode() == null ? "" : it.getStatusCode())),
                    safe(String.valueOf(it.getDurationMs() == null ? "" : it.getDurationMs()))
            )).collect(Collectors.joining("\n"));
            byte[] bytes = (header + body).getBytes(StandardCharsets.UTF_8);
            return ReturnResult.success(bytes);
        } catch (Exception e) {
            log.error("导出systemserverLog失败", e);
            return ReturnResult.error("导出失败: " + e.getMessage());
        }
    }

    /**
     * 清理指定时间之前的系统服务器日志
     *
     * @param beforeTime 清理时间点，该时间点之前的数据将被删除，例如：LocalDateTime.of(2023, 1, 1, 0, 0, 0)
     * @return 删除的记录数
     */
    @Override
    public ReturnResult<Integer> cleanup(LocalDateTime beforeTime) {
        try {
            int cnt = cleanupLogFiles(beforeTime);
            return ReturnResult.success(cnt);
        } catch (Exception e) {
            log.error("清理systemserverLog失败", e);
            return ReturnResult.error("清理失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> queryFirewallEvents(Integer serverId,
                                                                       String clientIp,
                                                                       String action,
                                                                       LocalDateTime startTime,
                                                                       LocalDateTime endTime,
                                                                       Integer limit) {
        try {
            List<Map<String, Object>> runtimeEvents = queryRuntimeFirewallEvents(serverId, clientIp, action, startTime, endTime, limit);
            if (!runtimeEvents.isEmpty()) {
                return ReturnResult.success(runtimeEvents);
            }
            List<Map<String, Object>> fallbackEvents = queryFileFirewallEvents(serverId, clientIp, action, startTime, endTime, limit);
            return ReturnResult.success(fallbackEvents);
        } catch (Exception e) {
            log.error("查询防火墙事件失败: serverId={}", serverId, e);
            return ReturnResult.error("查询防火墙事件失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<FirewallMetrics> queryFirewallMetrics(Integer serverId,
                                                              LocalDateTime startTime,
                                                              LocalDateTime endTime) {
        try {
            FirewallMetrics runtimeMetrics = queryRuntimeFirewallMetrics(serverId, startTime, endTime);
            if (runtimeMetrics != null && runtimeMetrics.getTotal() > 0) {
                return ReturnResult.success(runtimeMetrics);
            }
            return ReturnResult.success(buildMetricsFromLogs(serverId, startTime, endTime));
        } catch (Exception e) {
            log.error("查询防火墙指标失败: serverId={}", serverId, e);
            return ReturnResult.error("查询防火墙指标失败: " + e.getMessage());
        }
    }

    /**
     * 异步记录系统服务器日志
     *
     * @param systemServerLog 系统服务器日志实体对象，例如：new SystemServerLog()
     */
    @Override
    public void asyncRecord(SystemServerLog systemServerLog) {
        if (null == systemServerLog) return;
        executor.execute(() -> {
            try {
                // 兜底填充
                if (systemServerLog.getStoreTime() == null) {
                    systemServerLog.setStoreTime(LocalDateTime.now());
                }
                appendLogToFile(systemServerLog);
            } catch (Exception e) {
                log.error("保存systemserverLog失败", e);
            }
        });
    }

    private List<SystemServerLog> readLogsFromFile(Integer serverId,
                                                   String filterType,
                                                   String processStatus,
                                                   String clientIp,
                                                   LocalDateTime startTime,
                                                   LocalDateTime endTime) throws IOException {
        if (serverId == null || serverId <= 0) {
            return List.of();
        }
        Path serverDir = Path.of(LOG_BASE_DIR, String.valueOf(serverId));
        if (!Files.exists(serverDir)) {
            return List.of();
        }
        List<SystemServerLog> result = new ArrayList<>();
        try (var dateDirs = Files.list(serverDir)) {
            for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                Path logFile = dateDir.resolve("server.log");
                if (!Files.exists(logFile)) {
                    continue;
                }
                for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
                    SystemServerLog item = parseLogLine(line);
                    if (item == null) {
                        continue;
                    }
                    if (!matches(item, filterType, processStatus, clientIp, startTime, endTime)) {
                        continue;
                    }
                    result.add(item);
                }
            }
        }
        result.sort(Comparator.comparing(SystemServerLog::getStoreTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed());
        return result;
    }

    private List<Map<String, Object>> queryRuntimeFirewallEvents(Integer serverId,
                                                                 String clientIp,
                                                                 String action,
                                                                 LocalDateTime startTime,
                                                                 LocalDateTime endTime,
                                                                 Integer limit) {
        FirewallServletFilter firewallFilter = getRunningFirewallFilter(serverId);
        if (firewallFilter == null) {
            return List.of();
        }
        FirewallStorageSpi storage = resolveFirewallStorage(firewallFilter);
        FirewallEventQuery query = new FirewallEventQuery();
        query.setServerId(serverId);
        query.setServerKey(String.valueOf(serverId));
        query.setClientIp(clientIp);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setLimit(limit == null ? 200 : limit);
        if (!StringUtils.isEmpty(action)) {
            try {
                query.setAction(FirewallAction.valueOf(action.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return storage.queryEvents(query).stream().map(this::toEventMap).toList();
    }

    private FirewallMetrics queryRuntimeFirewallMetrics(Integer serverId,
                                                        LocalDateTime startTime,
                                                        LocalDateTime endTime) {
        FirewallServletFilter firewallFilter = getRunningFirewallFilter(serverId);
        if (firewallFilter == null) {
            return null;
        }
        FirewallStorageSpi storage = resolveFirewallStorage(firewallFilter);
        FirewallMetricsQuery query = new FirewallMetricsQuery();
        query.setServerId(serverId);
        query.setServerKey(String.valueOf(serverId));
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        FirewallMetrics metrics = storage.queryMetrics(query);
        if (metrics == null) {
            return null;
        }
        FirewallMetrics current = firewallFilter.currentMetrics(String.valueOf(serverId), serverId);
        if (metrics.getTotal() <= 0 && current != null) {
            return current;
        }
        return metrics;
    }

    private List<Map<String, Object>> queryFileFirewallEvents(Integer serverId,
                                                              String clientIp,
                                                              String action,
                                                              LocalDateTime startTime,
                                                              LocalDateTime endTime,
                                                              Integer limit) throws IOException {
        if (serverId == null || serverId <= 0) {
            return List.of();
        }
        String processStatus = null;
        if (!StringUtils.isEmpty(action)) {
            String normalized = action.trim().toUpperCase(Locale.ROOT);
            if ("RATE_LIMIT".equals(normalized)) {
                processStatus = "RATE_LIMIT";
            } else if ("DENY".equals(normalized)) {
                processStatus = "DENY";
            } else if ("ALLOW".equals(normalized)) {
                processStatus = "ALLOW";
            } else if ("BLACKLIST".equals(normalized)) {
                processStatus = "BLACKLIST";
            }
        }
        List<SystemServerLog> logs = readLogsFromFile(serverId, null, processStatus, clientIp, startTime, endTime);
        int max = limit == null || limit <= 0 ? 200 : limit;
        return logs.stream()
                .map(this::toEventMap)
                .limit(max)
                .toList();
    }

    private FirewallMetrics buildMetricsFromLogs(Integer serverId,
                                                 LocalDateTime startTime,
                                                 LocalDateTime endTime) throws IOException {
        FirewallMetrics metrics = new FirewallMetrics();
        metrics.setServerId(serverId);
        metrics.setServerKey(String.valueOf(serverId));
        metrics.setWindowStart(startTime);
        metrics.setWindowEnd(endTime);

        List<SystemServerLog> logs = readLogsFromFile(serverId, null, null, null, startTime, endTime);
        metrics.setTotal(logs.size());
        for (SystemServerLog logItem : logs) {
            String action = StringUtils.nullToDefault(logItem.getActionStatus(), "").toUpperCase(Locale.ROOT);
            if ("PASS".equals(action)) {
                metrics.setAllowed(metrics.getAllowed() + 1);
            }
            if ("DENY".equals(action)) {
                metrics.setDenied(metrics.getDenied() + 1);
            }
            String reason = StringUtils.nullToDefault(logItem.getRuleCategory(), "").toUpperCase(Locale.ROOT);
            String process = StringUtils.nullToDefault(logItem.getProcessStatus(), "").toUpperCase(Locale.ROOT);
            if (reason.contains("RATE_LIMIT") || process.contains("RATE_LIMIT")) {
                metrics.setRateLimited(metrics.getRateLimited() + 1);
            }
            if (reason.contains("BLACKLIST") || process.contains("BLACKLIST")) {
                metrics.setBlacklistHit(metrics.getBlacklistHit() + 1);
            }
            if (reason.contains("WHITELIST") || process.contains("WHITELIST")) {
                metrics.setWhitelistHit(metrics.getWhitelistHit() + 1);
            }
            if (reason.contains("REDIRECT") || process.contains("REDIRECT")) {
                metrics.setRedirected(metrics.getRedirected() + 1);
            }
            if (reason.contains("PROXY_PASS") || process.contains("PROXY_PASS")) {
                metrics.setProxied(metrics.getProxied() + 1);
            }
        }
        return metrics;
    }

    private FirewallServletFilter getRunningFirewallFilter(Integer serverId) {
        if (serverId == null || serverId <= 0) {
            return null;
        }
        ProtocolServer runningServer = systemServerService.getRunningServerInstance(serverId);
        if (runningServer == null) {
            return null;
        }
        return runningServer.getFilter(FirewallServletFilter.class);
    }

    private FirewallStorageSpi resolveFirewallStorage(FirewallServletFilter filter) {
        if (filter == null || filter.getConfigurationObject() == null) {
            return new MemoryFirewallStorageSpi();
        }
        String storageType = filter.getConfigurationObject().getStorageType();
        FirewallStorageSpi storage = ServiceProvider.of(FirewallStorageSpi.class).getNewExtension(storageType);
        if (storage != null) {
            return storage;
        }
        storage = ServiceProvider.of(FirewallStorageSpi.class).getNewExtension("memory-firewall-storage");
        return storage != null ? storage : new MemoryFirewallStorageSpi();
    }

    private Map<String, Object> toEventMap(FirewallEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serverKey", event.getServerKey());
        row.put("serverId", event.getServerId());
        row.put("requestId", event.getRequestId());
        row.put("clientIp", event.getClientIp());
        row.put("method", event.getMethod());
        row.put("path", event.getPath());
        row.put("action", event.getAction() == null ? "" : event.getAction().name());
        row.put("reason", event.getReason());
        row.put("statusCode", event.getStatusCode());
        row.put("durationMs", event.getDurationMs());
        row.put("eventTime", fmtSafe(event.getEventTime(), TIME_FORMATTER));
        return row;
    }

    private Map<String, Object> toEventMap(SystemServerLog logItem) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serverKey", logItem.getServerId() == null ? "" : String.valueOf(logItem.getServerId()));
        row.put("serverId", logItem.getServerId());
        row.put("requestId", logItem.getRequestId());
        row.put("clientIp", logItem.getClientIp());
        row.put("clientGeo", logItem.getClientGeo());
        row.put("method", logItem.getMethod());
        row.put("path", logItem.getPath());
        row.put("host", logItem.getHost());
        row.put("referer", logItem.getReferer());
        row.put("action", StringUtils.nullToDefault(logItem.getActionStatus(), ""));
        row.put("reason", firstNonEmpty(logItem.getDecisionReason(), logItem.getRuleCategory(), logItem.getProcessStatus()));
        row.put("statusCode", logItem.getStatusCode());
        row.put("durationMs", logItem.getDurationMs());
        row.put("eventTime", fmtSafe(logItem.getStoreTime(), TIME_FORMATTER));
        return row;
    }

    private boolean matches(SystemServerLog logItem,
                            String filterType,
                            String processStatus,
                            String clientIp,
                            LocalDateTime startTime,
                            LocalDateTime endTime) {
        if (!StringUtils.isEmpty(filterType) && (logItem.getFilterType() == null || !logItem.getFilterType().contains(filterType))) {
            return false;
        }
        if (!StringUtils.isEmpty(processStatus) && (logItem.getProcessStatus() == null || !logItem.getProcessStatus().contains(processStatus))) {
            return false;
        }
        if (!StringUtils.isEmpty(clientIp) && (logItem.getClientIp() == null || !logItem.getClientIp().contains(clientIp))) {
            return false;
        }
        if (startTime != null && (logItem.getStoreTime() == null || logItem.getStoreTime().isBefore(startTime))) {
            return false;
        }
        if (endTime != null && (logItem.getStoreTime() == null || logItem.getStoreTime().isAfter(endTime))) {
            return false;
        }
        return true;
    }

    private void appendLogToFile(SystemServerLog logItem) throws IOException {
        Integer serverId = logItem.getServerId();
        if (serverId == null || serverId <= 0) {
            return;
        }
        LocalDateTime storeTime = logItem.getStoreTime() == null ? LocalDateTime.now() : logItem.getStoreTime();
        Path dateDir = Path.of(LOG_BASE_DIR, String.valueOf(serverId), storeTime.toLocalDate().format(DATE_DIR_FORMATTER));
        Files.createDirectories(dateDir);
        Path logFile = dateDir.resolve("server.log");
        String line = toLine(logItem) + System.lineSeparator();
        Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String toLine(SystemServerLog logItem) {
        return String.join("|",
                fmtSafe(logItem.getStoreTime(), TIME_FORMATTER),
                fmtSafe(logItem.getAccessTime(), TIME_FORMATTER),
                safe(String.valueOf(logItem.getServerId() == null ? "" : logItem.getServerId())),
                safe(logItem.getFilterType()),
                safe(logItem.getRuleCategory()),
                safe(logItem.getActionStatus()),
                safe(logItem.getProcessStatus()),
                safe(logItem.getMethod()),
                safe(logItem.getPath()),
                safe(logItem.getHost()),
                safe(logItem.getReferer()),
                safe(logItem.getRequestId()),
                safe(logItem.getClientIp()),
                safe(logItem.getClientGeo()),
                safe(logItem.getUserAgent()),
                safe(logItem.getDecisionReason()),
                safe(String.valueOf(logItem.getStatusCode() == null ? "" : logItem.getStatusCode())),
                safe(String.valueOf(logItem.getDurationMs() == null ? "" : logItem.getDurationMs())));
    }

    private SystemServerLog parseLogLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length < 8) {
            return null;
        }
        SystemServerLog item = new SystemServerLog();
        item.setStoreTime(parseTime(parts[0]));
        item.setAccessTime(parseTime(parts[1]));
        item.setServerId(parseInteger(parts[2]));
        item.setFilterType(parts[3]);
        // 兼容旧格式：
        // - v1：store|access|serverId|filterType|processStatus|clientIp|clientGeo|duration
        // - v2：store|access|serverId|filterType|actionStatus|processStatus|clientIp|clientGeo|userAgent|duration
        // - v3：store|access|serverId|filterType|ruleCategory|actionStatus|processStatus|method|path|requestId|clientIp|clientGeo|userAgent|reason|duration
        // - v4：store|access|serverId|filterType|ruleCategory|actionStatus|processStatus|method|path|host|referer|requestId|clientIp|clientGeo|userAgent|reason|statusCode|duration
        if (parts.length >= 18) {
            item.setRuleCategory(parts[4]);
            item.setActionStatus(parts[5]);
            item.setProcessStatus(parts[6]);
            item.setMethod(parts[7]);
            item.setPath(parts[8]);
            item.setHost(parts[9]);
            item.setReferer(parts[10]);
            item.setRequestId(parts[11]);
            item.setClientIp(parts[12]);
            item.setClientGeo(parts[13]);
            item.setUserAgent(parts[14]);
            item.setDecisionReason(parts[15]);
            item.setStatusCode(parseInteger(parts[16]));
            item.setDurationMs(parseLong(parts[17]));
        } else if (parts.length >= 15) {
            item.setRuleCategory(parts[4]);
            item.setActionStatus(parts[5]);
            item.setProcessStatus(parts[6]);
            item.setMethod(parts[7]);
            item.setPath(parts[8]);
            item.setRequestId(parts[9]);
            item.setClientIp(parts[10]);
            item.setClientGeo(parts[11]);
            item.setUserAgent(parts[12]);
            item.setDecisionReason(parts[13]);
            item.setDurationMs(parseLong(parts[14]));
        } else if (parts.length >= 10) {
            item.setActionStatus(parts[4]);
            item.setProcessStatus(parts[5]);
            item.setClientIp(parts[6]);
            item.setClientGeo(parts[7]);
            item.setUserAgent(parts[8]);
            item.setDurationMs(parseLong(parts[9]));
            item.setRuleCategory(toRuleCategoryFallback(item.getProcessStatus()));
        } else {
            item.setProcessStatus(parts[4]);
            item.setActionStatus(toActionStatusFallback(parts[4]));
            item.setClientIp(parts[5]);
            item.setClientGeo(parts[6]);
            item.setDurationMs(parseLong(parts[7]));
            item.setRuleCategory(toRuleCategoryFallback(item.getProcessStatus()));
        }
        return item;
    }

    private String toActionStatusFallback(String processStatus) {
        if (StringUtils.isEmpty(processStatus)) {
            return "";
        }
        String up = processStatus.toUpperCase();
        if ("OK".equals(up) || up.contains("PASS") || up.contains("ALLOW")) {
            return "PASS";
        }
        if (up.contains("BLOCK") || up.contains("DENY") || up.contains("REJECT") || up.contains("LIMIT")) {
            return "DENY";
        }
        return "DENY";
    }

    private String toRuleCategoryFallback(String processStatus) {
        if (StringUtils.isEmpty(processStatus)) {
            return "UNKNOWN";
        }
        String up = processStatus.toUpperCase();
        if (up.contains("BLACKLIST")) return "BLACKLIST";
        if (up.contains("WHITELIST")) return "WHITELIST";
        if (up.contains("LIMIT")) return "RATE_LIMIT";
        return "UNKNOWN";
    }

    private int cleanupLogFiles(LocalDateTime beforeTime) throws IOException {
        if (beforeTime == null) {
            return 0;
        }
        Path baseDir = Path.of(LOG_BASE_DIR);
        if (!Files.exists(baseDir)) {
            return 0;
        }
        int deleted = 0;
        LocalDate beforeDate = beforeTime.toLocalDate();
        try (var serverDirs = Files.list(baseDir)) {
            for (Path serverDir : serverDirs.filter(Files::isDirectory).toList()) {
                try (var dateDirs = Files.list(serverDir)) {
                    for (Path dateDir : dateDirs.filter(Files::isDirectory).toList()) {
                        LocalDate date = parseDate(dateDir.getFileName().toString());
                        if (date == null || !date.isBefore(beforeDate)) {
                            continue;
                        }
                        Path logFile = dateDir.resolve("server.log");
                        if (Files.exists(logFile)) {
                            deleted += Files.readAllLines(logFile, StandardCharsets.UTF_8).size();
                        }
                        deleteDirectoryRecursively(dateDir);
                    }
                }
            }
        }
        return deleted;
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            for (Path item : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, DATE_DIR_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseTime(String text) {
        try {
            if (StringUtils.isEmpty(text)) {
                return null;
            }
            return LocalDateTime.parse(text, TIME_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseInteger(String text) {
        try {
            if (StringUtils.isEmpty(text)) {
                return null;
            }
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLong(String text) {
        try {
            if (StringUtils.isEmpty(text)) {
                return null;
            }
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fmtSafe(LocalDateTime time, DateTimeFormatter fmt) {
        return time == null ? "" : fmt.format(time);
    }

    private String safe(String v) {
        return StringUtils.isEmpty(v) ? "" : v.replaceAll(",", " ");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!StringUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }
}
