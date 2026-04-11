package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.oshi.support.Network;
import com.chua.oshi.support.Oshi;
import com.chua.ssh.support.client.LinuxExecClient;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerMetricsHistory;
import com.chua.starter.server.support.mapper.ServerMetricsHistoryMapper;
import com.chua.starter.server.support.model.ServerDiskPartitionView;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.model.ServerMetricsTaskSettings;
import com.chua.starter.server.support.model.ServerMetricsTaskSettingsRequest;
import com.chua.starter.server.support.model.ServerNetworkInterfaceView;
import com.chua.starter.server.support.model.ServerRealtimePayload;
import com.chua.starter.server.support.service.ServerAlertService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerMetricsService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import com.chua.winrm.support.client.WinRmExecClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerMetricsServiceImpl implements ServerMetricsService {

    private static final String SNAPSHOT_CACHE_KEY = "plugin:server:metrics:snapshot";
    private static final int MAX_HISTORY_POINTS = 720;
    private static final String METRICS_REFRESH_JOB_NAME = "serverMetricsRefreshJob";
    private static final String METRICS_REFRESH_JOB_DESC = "服务器指标采集任务";
    private static final String METRICS_REFRESH_SCHEDULE_TYPE = "fixed_ms";
    private static final String METRICS_CLEANUP_JOB_NAME = "serverMetricsCleanupJob";
    private static final String METRICS_CLEANUP_JOB_DESC = "服务器指标历史清理任务";
    private static final String METRICS_CLEANUP_CRON = "0 35 3 * * ?";
    private static final String LINUX_NETWORK_COUNTER_COMMAND =
            "rx_bytes=0; tx_bytes=0; rx_packets=0; tx_packets=0;"
                    + "for iface in $(ls /sys/class/net 2>/dev/null); do "
                    + "[ \"$iface\" = \"lo\" ] && continue; "
                    + "rx_bytes=$((rx_bytes + $(cat /sys/class/net/$iface/statistics/rx_bytes 2>/dev/null || echo 0))); "
                    + "tx_bytes=$((tx_bytes + $(cat /sys/class/net/$iface/statistics/tx_bytes 2>/dev/null || echo 0))); "
                    + "rx_packets=$((rx_packets + $(cat /sys/class/net/$iface/statistics/rx_packets 2>/dev/null || echo 0))); "
                    + "tx_packets=$((tx_packets + $(cat /sys/class/net/$iface/statistics/tx_packets 2>/dev/null || echo 0))); "
                    + "done; printf '%s,%s,%s,%s' \"$rx_bytes\" \"$tx_bytes\" \"$rx_packets\" \"$tx_packets\"";
    private static final String WINDOWS_NETWORK_COUNTER_COMMAND =
            "$rxBytes=0;$txBytes=0;$rxPackets=0;$txPackets=0;"
                    + "Get-NetAdapterStatistics -ErrorAction SilentlyContinue | ForEach-Object {"
                    + "$rxBytes+=[double]($_.ReceivedBytes);"
                    + "$txBytes+=[double]($_.SentBytes);"
                    + "$rxPackets+=[double](($_.ReceivedUnicastPackets)+($_.ReceivedBroadcastPackets)+($_.ReceivedMulticastPackets));"
                    + "$txPackets+=[double](($_.SentUnicastPackets)+($_.SentBroadcastPackets)+($_.SentMulticastPackets));"
                    + "};"
                    + "Write-Output \"ioRead=$([math]::Round($rxBytes,2))\";"
                    + "Write-Output \"ioWrite=$([math]::Round($txBytes,2))\";"
                    + "Write-Output \"networkRxPackets=$([math]::Round($rxPackets,2))\";"
                    + "Write-Output \"networkTxPackets=$([math]::Round($txPackets,2))\"";
    private static final String UNIX_FACTS_COMMAND =
            "hostname_value=$(hostname 2>/dev/null || uname -n);"
                    + "os_value=$(if [ -f /etc/os-release ]; then . /etc/os-release && printf '%s' \"$PRETTY_NAME\"; else uname -srm; fi);"
                    + "kernel_value=$(uname -srmo 2>/dev/null || uname -sr);"
                    + "public_value=$(((curl -4 -s --max-time 3 https://ifconfig.me/ip || wget -qO- --timeout=3 https://ifconfig.me/ip) 2>/dev/null | head -n 1));"
                    + "printf 'hostName=%s\\nactualOsName=%s\\nactualKernel=%s\\npublicIp=%s\\n' \"$hostname_value\" \"$os_value\" \"$kernel_value\" \"$public_value\"";
    private static final String UNIX_DISK_COMMAND =
            "df -B1 -P -x tmpfs -x devtmpfs 2>/dev/null | tail -n +2";
    private static final String UNIX_NETWORK_COMMAND =
            "for iface in $(ls /sys/class/net 2>/dev/null); do "
                    + "rx=$(cat /sys/class/net/$iface/statistics/rx_bytes 2>/dev/null || echo 0); "
                    + "tx=$(cat /sys/class/net/$iface/statistics/tx_bytes 2>/dev/null || echo 0); "
                    + "rxp=$(cat /sys/class/net/$iface/statistics/rx_packets 2>/dev/null || echo 0); "
                    + "txp=$(cat /sys/class/net/$iface/statistics/tx_packets 2>/dev/null || echo 0); "
                    + "state=$(cat /sys/class/net/$iface/operstate 2>/dev/null || echo unknown); "
                    + "mac=$(cat /sys/class/net/$iface/address 2>/dev/null || echo ''); "
                    + "ipv4=$(ip -o -4 addr show dev \"$iface\" 2>/dev/null | awk '{print $4}' | head -n 1 | cut -d/ -f1); "
                    + "printf 'iface=%s|state=%s|mac=%s|ipv4=%s|rx=%s|tx=%s|rxp=%s|txp=%s\\n' \"$iface\" \"$state\" \"$mac\" \"$ipv4\" \"$rx\" \"$tx\" \"$rxp\" \"$txp\"; "
                    + "done";
    private static final String WINDOWS_FACTS_COMMAND =
            "$public='';"
                    + "try{$public=(Invoke-RestMethod -UseBasicParsing -Uri 'https://ifconfig.me/ip' -TimeoutSec 3)}catch{};"
                    + "$os=Get-CimInstance Win32_OperatingSystem;"
                    + "Write-Output \"hostName=$env:COMPUTERNAME\";"
                    + "Write-Output \"actualOsName=$($os.Caption)\";"
                    + "Write-Output \"actualKernel=$($os.Version)\";"
                    + "Write-Output \"publicIp=$public\";";
    private static final String WINDOWS_DISK_COMMAND =
            "$items=Get-CimInstance Win32_LogicalDisk -Filter \"DriveType=3\" | ForEach-Object {"
                    + "$total=[int64]($_.Size);"
                    + "$free=[int64]($_.FreeSpace);"
                    + "$used=if ($total -gt 0) { $total - $free } else { 0 };"
                    + "[pscustomobject]@{"
                    + "name=$_.DeviceID;"
                    + "mountPoint=$_.DeviceID;"
                    + "fileSystem=$_.FileSystem;"
                    + "label=$_.VolumeName;"
                    + "totalBytes=$total;"
                    + "usedBytes=$used;"
                    + "freeBytes=$free;"
                    + "usagePercent=if ($total -gt 0) { [math]::Round(($used / $total) * 100, 2) } else { 0 };"
                    + "status='mounted'"
                    + "}"
                    + "};"
                    + "$items | ConvertTo-Json -Compress";
    private static final String WINDOWS_NETWORK_COMMAND =
            "$items=Get-NetAdapter -ErrorAction SilentlyContinue | ForEach-Object {"
                    + "$stats=Get-NetAdapterStatistics -Name $_.Name -ErrorAction SilentlyContinue;"
                    + "$ip=(Get-NetIPAddress -InterfaceIndex $_.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty IPAddress);"
                    + "[pscustomobject]@{"
                    + "name=$_.Name;"
                    + "displayName=$_.InterfaceDescription;"
                    + "status=$_.Status;"
                    + "ipv4=$ip;"
                    + "macAddress=$_.MacAddress;"
                    + "receivedBytes=[int64]($stats.ReceivedBytes);"
                    + "transmittedBytes=[int64]($stats.SentBytes);"
                    + "receivedPackets=[int64](($stats.ReceivedUnicastPackets)+($stats.ReceivedBroadcastPackets)+($stats.ReceivedMulticastPackets));"
                    + "transmittedPackets=[int64](($stats.SentUnicastPackets)+($stats.SentBroadcastPackets)+($stats.SentMulticastPackets))"
                    + "}"
                    + "};"
                    + "$items | ConvertTo-Json -Compress";

    private final ServerHostService serverHostService;
    private final ServerManagementProperties properties;
    private final ServerRealtimePublisher serverRealtimePublisher;
    private final ServerAlertService serverAlertService;
    private final ServerMetricsHistoryMapper serverMetricsHistoryMapper;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final ObjectProvider<JobDynamicConfigService> jobDynamicConfigServiceProvider;
    private final ObjectProvider<JobProperties> jobPropertiesProvider;
    private final ObjectMapper objectMapper;

    private final Map<Integer, ServerMetricsSnapshot> snapshotCache = new ConcurrentHashMap<>();
    private final Map<Integer, Deque<ServerMetricsSnapshot>> historyCache = new ConcurrentHashMap<>();
    private final Map<Integer, NetworkCounterState> networkCounterCache = new ConcurrentHashMap<>();

    private volatile boolean redisCacheLoaded;
    private volatile long lastRefreshAt;
    private volatile long nextRefreshAt;

    /**
     * 启动时把 job-starter 配置与本地下一次执行时间对齐。
     */
    @PostConstruct
    void initializeMetricsScheduling() {
        synchronizeMetricsJobs();
        if (properties.getMetrics().isEnable()) {
            nextRefreshAt = System.currentTimeMillis() + Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L);
        }
    }

    /**
     * 读取单个服务器当前缓存的最新指标快照。
     */
    @Override
    public ServerMetricsSnapshot getSnapshot(Integer serverId) {
        if (serverId == null) {
            return null;
        }
        ensureSnapshotCacheLoaded();
        return snapshotCache.get(serverId);
    }

    /**
     * 返回指标卡片弹框所需的磁盘、网卡与系统事实明细。
     */
    @Override
    public ServerMetricsDetail getDetail(Integer serverId) {
        if (serverId == null) {
            return null;
        }
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            return null;
        }
        ServerMetricsSnapshot snapshot = getSnapshot(serverId);
        if (snapshot == null) {
            snapshot = collectSnapshot(host);
            if (snapshot != null && snapshot.getServerId() != null) {
                snapshotCache.put(snapshot.getServerId(), snapshot);
                cacheSnapshot(snapshot);
            }
        }
        return collectDetail(host, snapshot);
    }

    /**
     * 列出所有服务器的最新快照；缓存为空时主动做一次采集。
     */
    @Override
    public List<ServerMetricsSnapshot> listSnapshots() {
        ensureSnapshotCacheLoaded();
        if (snapshotCache.isEmpty()) {
            return refreshMetrics();
        }
        List<ServerMetricsSnapshot> snapshots = new ArrayList<>();
        for (ServerHost host : serverHostService.listHosts(null, null, null)) {
            ServerMetricsSnapshot snapshot = snapshotCache.get(host.getServerId());
            if (snapshot == null) {
                snapshot = buildDisabledSnapshot(host);
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    /**
     * 立即采集全部服务器指标，并同步刷新缓存、历史、告警与实时推送。
     */
    @Override
    public List<ServerMetricsSnapshot> refreshMetrics() {
        List<ServerMetricsSnapshot> snapshots = new ArrayList<>();
        for (ServerHost host : serverHostService.listHosts(null, null, null)) {
            ServerMetricsSnapshot snapshot = collectSnapshot(host);
            if (host.getServerId() != null) {
                snapshotCache.put(host.getServerId(), snapshot);
                recordHistory(snapshot);
            }
            snapshots.add(snapshot);
            cacheSnapshot(snapshot);
            serverAlertService.processSnapshot(snapshot);
            publishSnapshot(snapshot);
        }
        lastRefreshAt = System.currentTimeMillis();
        nextRefreshAt = lastRefreshAt + Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L);
        return snapshots;
    }

    /**
     * 查询指定服务器的指标历史，优先走持久化表，缓存只作为兜底。
     */
    @Override
    public List<ServerMetricsSnapshot> listHistory(Integer serverId, Integer minutes) {
        if (serverId == null) {
            return Collections.emptyList();
        }
        ensureSnapshotCacheLoaded();
        List<ServerMetricsSnapshot> storedHistory = listPersistedHistory(serverId, minutes);
        if (!storedHistory.isEmpty()) {
            return storedHistory;
        }
        Deque<ServerMetricsSnapshot> history = historyCache.get(serverId);
        if ((history == null || history.isEmpty()) && snapshotCache.get(serverId) != null) {
            recordHistory(snapshotCache.get(serverId));
            history = historyCache.get(serverId);
        }
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        long cutoff = resolveHistoryCutoff(minutes);
        List<ServerMetricsSnapshot> result = new ArrayList<>();
        for (ServerMetricsSnapshot item : history) {
            if (item == null) {
                continue;
            }
            long collectTimestamp = item.getCollectTimestamp() == null ? 0L : item.getCollectTimestamp();
            if (cutoff > 0L && collectTimestamp < cutoff) {
                continue;
            }
            result.add(cloneSnapshot(item));
        }
        return result;
    }

    /**
     * 返回当前指标任务配置与调度状态。
     */
    @Override
    public ServerMetricsTaskSettings getTaskSettings() {
        synchronizeMetricsJobs();
        return buildTaskSettings();
    }

    /**
     * 更新采集开关、频率、缓存策略，并同步到底层调度器。
     */
    @Override
    public ServerMetricsTaskSettings updateTaskSettings(ServerMetricsTaskSettingsRequest request) {
        if (request == null) {
            return buildTaskSettings();
        }
        if (request.getEnabled() != null) {
            properties.getMetrics().setEnable(request.getEnabled());
        }
        if (request.getRefreshIntervalMs() != null) {
            properties.getMetrics().setRefreshIntervalMs(Math.max(request.getRefreshIntervalMs(), 1000L));
        }
        if (request.getTimeoutMs() != null) {
            properties.getMetrics().setTimeoutMs(Math.max(request.getTimeoutMs(), 1000));
        }
        if (request.getCacheEnabled() != null) {
            properties.getMetrics().setCacheEnabled(request.getCacheEnabled());
        }
        if (request.getCacheTtlSeconds() != null) {
            properties.getMetrics().setCacheTtlSeconds(Math.max(request.getCacheTtlSeconds(), 60L));
        }
        if (Boolean.TRUE.equals(properties.getMetrics().isEnable())) {
            long now = System.currentTimeMillis();
            nextRefreshAt = now + Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L);
        } else {
            nextRefreshAt = 0L;
        }
        synchronizeMetricsJobs();
        return buildTaskSettings();
    }

    /**
     * job-starter 的指标采集入口，供统一任务中心调用。
     */
    @Job(value = METRICS_REFRESH_JOB_NAME, desc = METRICS_REFRESH_JOB_DESC)
    public void executeRefreshJob() {
        if (!properties.getMetrics().isEnable()) {
            nextRefreshAt = 0L;
            return;
        }
        refreshMetrics();
    }

    /**
     * job-starter 的历史清理入口，定期清空过期指标数据。
     */
    @Job(value = METRICS_CLEANUP_JOB_NAME, desc = METRICS_CLEANUP_JOB_DESC)
    public void executeCleanupJob() {
        cleanupExpiredHistoryInternal();
    }

    /**
     * job-starter 没有启用配置表时，退回到进程内 fixedDelay 调度，保证测试环境仍可采集。
     */
    @Scheduled(fixedDelay = 1000L)
    public void scheduledRefreshFallback() {
        if (isJobTableSchedulingEnabled()) {
            return;
        }
        if (!properties.getMetrics().isEnable()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextRefreshAt > now) {
            return;
        }
        executeRefreshJob();
    }

    /**
     * job-starter 不接管时，继续由本地调度兜底清理过期历史。
     */
    @Scheduled(cron = "0 35 3 * * ?")
    public void cleanupExpiredHistoryFallback() {
        if (isJobTableSchedulingEnabled()) {
            return;
        }
        cleanupExpiredHistoryInternal();
    }

    /**
     * 聚合页面配置、最近执行信息和 job-starter 任务状态，避免前端再自行拼装调度语义。
     */
    private ServerMetricsTaskSettings buildTaskSettings() {
        MetricsRefreshJobState jobState = resolveRefreshJobState();
        Long resolvedNextRefreshAt = properties.getMetrics().isEnable()
                ? (jobState.nextTriggerAt() != null
                ? jobState.nextTriggerAt()
                : (nextRefreshAt > 0L ? nextRefreshAt
                : System.currentTimeMillis() + Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L)))
                : null;
        String status = properties.getMetrics().isEnable() ? "RUNNING" : "STOPPED";
        if (jobState.jobEnabled() && !StringUtils.hasText(jobState.jobStatus()) && properties.getMetrics().isEnable()) {
            status = "PENDING";
        } else if (StringUtils.hasText(jobState.jobStatus())) {
            status = jobState.jobStatus();
        }
        return ServerMetricsTaskSettings.builder()
                .enabled(properties.getMetrics().isEnable())
                .schedulerMode(jobState.schedulerMode())
                .jobEnabled(jobState.jobEnabled())
                .refreshIntervalMs(Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L))
                .timeoutMs(Math.max(properties.getMetrics().getTimeoutMs(), 1000))
                .cacheEnabled(properties.getMetrics().isCacheEnabled())
                .cacheTtlSeconds(Math.max(properties.getMetrics().getCacheTtlSeconds(), 60L))
                .lastRefreshAt(lastRefreshAt > 0L ? lastRefreshAt : null)
                .nextRefreshAt(resolvedNextRefreshAt)
                .historyLimit(MAX_HISTORY_POINTS)
                .status(status)
                .jobId(jobState.jobId())
                .jobNo(jobState.jobNo())
                .jobName(jobState.jobName())
                .jobScheduleType(jobState.jobScheduleType())
                .jobScheduleTime(jobState.jobScheduleTime())
                .jobStatus(jobState.jobStatus())
                .jobLastTriggerAt(jobState.lastTriggerAt())
                .jobNextTriggerAt(jobState.nextTriggerAt())
                .manualTriggerSupported(Boolean.TRUE)
                .build();
    }

    /**
     * 把指标采集和历史清理两个任务同步到 job-starter，页面修改后这里负责落地到底层调度器。
     */
    private void synchronizeMetricsJobs() {
        if (!isJobTableSchedulingEnabled()) {
            return;
        }
        JobDynamicConfigService service = jobDynamicConfigServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            upsertRefreshJob(service);
            upsertCleanupJob(service);
        } catch (Exception ex) {
            log.warn("同步服务器指标任务到 job-starter 失败: {}", ex.getMessage());
        }
    }

    /**
     * 创建或更新固定间隔的指标采集任务，真正的执行入口仍然是带 @Job 的 executeRefreshJob。
     */
    private void upsertRefreshJob(JobDynamicConfigService service) {
        SysJob job = service.getJobByName(METRICS_REFRESH_JOB_NAME);
        boolean running = properties.getMetrics().isEnable();
        long intervalMs = Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L);
        if (job == null) {
            job = new SysJob();
            job.setJobName(METRICS_REFRESH_JOB_NAME);
            job.setJobScheduleType(METRICS_REFRESH_SCHEDULE_TYPE);
            job.setJobScheduleTime(String.valueOf(intervalMs));
            job.setJobExecuteBean(METRICS_REFRESH_JOB_NAME);
            job.setJobDesc(METRICS_REFRESH_JOB_DESC);
            job.setJobTriggerStatus(running ? 1 : 0);
            service.createJob(job);
            if (running && job.getJobId() != null) {
                service.startJob(job.getJobId());
            }
            return;
        }
        job.setJobScheduleType(METRICS_REFRESH_SCHEDULE_TYPE);
        job.setJobScheduleTime(String.valueOf(intervalMs));
        job.setJobExecuteBean(METRICS_REFRESH_JOB_NAME);
        job.setJobDesc(METRICS_REFRESH_JOB_DESC);
        job.setJobTriggerStatus(running ? 1 : 0);
        service.updateJob(job);
        if (job.getJobId() == null) {
            return;
        }
        if (running) {
            service.startJob(job.getJobId());
        } else {
            service.stopJob(job.getJobId());
        }
    }

    /**
     * 创建或更新每天低峰执行的历史清理任务，避免历史表和缓存无限膨胀。
     */
    private void upsertCleanupJob(JobDynamicConfigService service) {
        SysJob job = service.getJobByName(METRICS_CLEANUP_JOB_NAME);
        if (job == null) {
            job = new SysJob();
            job.setJobName(METRICS_CLEANUP_JOB_NAME);
            job.setJobScheduleType("cron");
            job.setJobScheduleTime(METRICS_CLEANUP_CRON);
            job.setJobExecuteBean(METRICS_CLEANUP_JOB_NAME);
            job.setJobDesc(METRICS_CLEANUP_JOB_DESC);
            job.setJobTriggerStatus(1);
            service.createJob(job);
            if (job.getJobId() != null) {
                service.startJob(job.getJobId());
            }
            return;
        }
        job.setJobScheduleType("cron");
        job.setJobScheduleTime(METRICS_CLEANUP_CRON);
        job.setJobExecuteBean(METRICS_CLEANUP_JOB_NAME);
        job.setJobDesc(METRICS_CLEANUP_JOB_DESC);
        job.setJobTriggerStatus(1);
        service.updateJob(job);
        if (job.getJobId() != null) {
            service.startJob(job.getJobId());
        }
    }

    /**
     * 统一解析当前任务实际由谁调度，前端据此区分 job-starter 与本地兜底两种模式。
     */
    private MetricsRefreshJobState resolveRefreshJobState() {
        if (!isJobTableSchedulingEnabled()) {
            return new MetricsRefreshJobState(
                    "LOCAL_FALLBACK",
                    Boolean.FALSE,
                    null,
                    null,
                    METRICS_REFRESH_JOB_NAME,
                    METRICS_REFRESH_SCHEDULE_TYPE,
                    String.valueOf(Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L)),
                    properties.getMetrics().isEnable() ? "RUNNING" : "STOPPED",
                    lastRefreshAt > 0L ? lastRefreshAt : null,
                    properties.getMetrics().isEnable() ? nextRefreshAt : null);
        }
        JobDynamicConfigService service = jobDynamicConfigServiceProvider.getIfAvailable();
        if (service == null) {
            return new MetricsRefreshJobState(
                    "JOB_TABLE_UNAVAILABLE",
                    Boolean.FALSE,
                    null,
                    null,
                    METRICS_REFRESH_JOB_NAME,
                    METRICS_REFRESH_SCHEDULE_TYPE,
                    String.valueOf(Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L)),
                    "UNAVAILABLE",
                    lastRefreshAt > 0L ? lastRefreshAt : null,
                    properties.getMetrics().isEnable() ? nextRefreshAt : null);
        }
        SysJob job = service.getJobByName(METRICS_REFRESH_JOB_NAME);
        if (job == null) {
            return new MetricsRefreshJobState(
                    "JOB_TABLE",
                    Boolean.TRUE,
                    null,
                    null,
                    METRICS_REFRESH_JOB_NAME,
                    METRICS_REFRESH_SCHEDULE_TYPE,
                    String.valueOf(Math.max(properties.getMetrics().getRefreshIntervalMs(), 1000L)),
                    properties.getMetrics().isEnable() ? "PENDING" : "STOPPED",
                    lastRefreshAt > 0L ? lastRefreshAt : null,
                    properties.getMetrics().isEnable() ? nextRefreshAt : null);
        }
        return new MetricsRefreshJobState(
                "JOB_TABLE",
                Boolean.TRUE,
                job.getJobId(),
                job.getJobNo(),
                job.getJobName(),
                job.getJobScheduleType(),
                job.getJobScheduleTime(),
                safeStatusName(job.getJobTriggerStatus()),
                job.getJobTriggerLastTime() != null && job.getJobTriggerLastTime() > 0L
                        ? job.getJobTriggerLastTime() : (lastRefreshAt > 0L ? lastRefreshAt : null),
                job.getJobTriggerNextTime() != null && job.getJobTriggerNextTime() > 0L
                        ? job.getJobTriggerNextTime() : (properties.getMetrics().isEnable() ? nextRefreshAt : null));
    }

    /**
     * 只有 job-starter 开启并启用配置表时，才让统一任务中心真正接管调度。
     */
    private boolean isJobTableSchedulingEnabled() {
        JobProperties jobProperties = jobPropertiesProvider.getIfAvailable();
        return jobProperties != null && jobProperties.isEnable() && jobProperties.isConfigTableEnabled();
    }

    private String safeStatusName(Integer status) {
        if (status == null) {
            return properties.getMetrics().isEnable() ? "RUNNING" : "STOPPED";
        }
        return status == 1 ? "RUNNING" : "STOPPED";
    }

    /**
     * 清理三天前的历史指标，避免明细查询表无限增长。
     */
    private void cleanupExpiredHistoryInternal() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
        try {
            serverMetricsHistoryMapper.delete(Wrappers.<ServerMetricsHistory>lambdaQuery()
                    .lt(ServerMetricsHistory::getCollectTimestamp, cutoff));
        } catch (Exception ignored) {
            // ignore cleanup failures
        }
    }

    private void publishSnapshot(ServerMetricsSnapshot snapshot) {
        if (snapshot == null || snapshot.getServerId() == null) {
            return;
        }
        serverRealtimePublisher.publish(
                ServerSocketEvents.MODULE,
                ServerSocketEvents.SERVER_METRICS,
                snapshot.getServerId(),
                ServerRealtimePayload.builder()
                        .serverId(snapshot.getServerId())
                        .serverCode(snapshot.getServerCode())
                        .status(snapshot.getStatus())
                        .online(snapshot.getOnline())
                        .latencyMs(snapshot.getLatencyMs())
                        .cpuUsage(snapshot.getCpuUsage())
                        .cpuCores(snapshot.getCpuCores())
                        .memoryUsage(snapshot.getMemoryUsage())
                        .memoryTotalBytes(snapshot.getMemoryTotalBytes())
                        .memoryUsedBytes(snapshot.getMemoryUsedBytes())
                        .diskUsage(snapshot.getDiskUsage())
                        .diskTotalBytes(snapshot.getDiskTotalBytes())
                        .diskUsedBytes(snapshot.getDiskUsedBytes())
                        .ioReadBytesPerSecond(snapshot.getIoReadBytesPerSecond())
                        .ioWriteBytesPerSecond(snapshot.getIoWriteBytesPerSecond())
                        .networkRxPacketsPerSecond(snapshot.getNetworkRxPacketsPerSecond())
                        .networkTxPacketsPerSecond(snapshot.getNetworkTxPacketsPerSecond())
                        .message(snapshot.getDetailMessage())
                        .finished(true)
                        .build());
    }

    /**
     * 根据服务器接入协议选择本机、SSH 或 WinRM 采集实现。
     */
    private ServerMetricsSnapshot collectSnapshot(ServerHost host) {
        if (host == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(host.getEnabled())) {
            return buildDisabledSnapshot(host);
        }
        try {
            return switch (StringUtils.hasText(host.getServerType()) ? host.getServerType().toUpperCase() : "LOCAL") {
                case "SSH" -> collectViaSsh(host);
                case "WINRM" -> collectViaWinRm(host);
                default -> collectLocal(host);
            };
        } catch (Exception e) {
            return ServerMetricsSnapshot.builder()
                    .serverId(host.getServerId())
                    .serverCode(host.getServerCode())
                    .status("OFFLINE")
                    .online(Boolean.FALSE)
                    .collectTimestamp(System.currentTimeMillis())
                    .detailMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 使用本机 Oshi 与 JDK 指标采集当前节点状态。
     */
    private ServerMetricsSnapshot collectLocal(ServerHost host) throws Exception {
        com.sun.management.OperatingSystemMXBean bean =
                ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        double cpuUsage = bean == null ? 0D : Math.max(bean.getCpuLoad(), 0D) * 100D;
        int cpuCores = bean == null ? Runtime.getRuntime().availableProcessors() : bean.getAvailableProcessors();
        long totalMemory = bean == null ? 0L : bean.getTotalMemorySize();
        long freeMemory = bean == null ? 0L : bean.getFreeMemorySize();
        long usedMemory = resolveUsedBytes(totalMemory, totalMemory - freeMemory);
        double memoryUsage = resolveUsagePercent(totalMemory, usedMemory);
        UsageStats diskStats = resolveLocalDiskStats();
        NetworkStats networkStats = resolveLocalNetworkStats(host);
        int latency = resolveLocalLatency(host);
        return ServerMetricsSnapshot.builder()
                .serverId(host.getServerId())
                .serverCode(host.getServerCode())
                .status("ONLINE")
                .online(Boolean.TRUE)
                .latencyMs(latency)
                .cpuUsage(round(cpuUsage))
                .cpuCores(cpuCores)
                .memoryUsage(round(memoryUsage))
                .memoryTotalBytes(totalMemory)
                .memoryUsedBytes(usedMemory)
                .diskUsage(round(diskStats.usagePercent()))
                .diskTotalBytes(diskStats.totalBytes())
                .diskUsedBytes(diskStats.usedBytes())
                .ioReadBytesPerSecond(round(networkStats.readBytesPerSecond()))
                .ioWriteBytesPerSecond(round(networkStats.writeBytesPerSecond()))
                .networkRxPacketsPerSecond(round(networkStats.readPacketsPerSecond()))
                .networkTxPacketsPerSecond(round(networkStats.writePacketsPerSecond()))
                .collectTimestamp(System.currentTimeMillis())
                .detailMessage("本机状态已更新")
                .build();
    }

    /**
     * 通过 SSH 执行 Linux 命令采集远程主机指标。
     */
    private ServerMetricsSnapshot collectViaSsh(ServerHost host) throws Exception {
        LinuxExecClient client = new LinuxExecClient(toClientSetting(host, 22));
        try {
            client.connect();
            long start = System.nanoTime();
            client.executeCommand("printf metrics", properties.getMetrics().getTimeoutMs());
            int latency = (int) ((System.nanoTime() - start) / 1_000_000L);
            int cpuCores = parseInteger(client.executeCommand(
                    "getconf _NPROCESSORS_ONLN",
                    properties.getMetrics().getTimeoutMs()).getOutput());
            double cpuUsage = parseDouble(client.executeCommand(
                    "vmstat 1 2 | tail -1 | awk '{print 100-$15}'",
                    properties.getMetrics().getTimeoutMs()).getOutput());
            UsageStats memoryStats = parseUsageStats(client.executeCommand(
                    "free -b | awk '/Mem:/ {print $2\",\"$3}'",
                    properties.getMetrics().getTimeoutMs()).getOutput());
            UsageStats diskStats = parseUsageStats(client.executeCommand(
                    "df -B1 -x tmpfs -x devtmpfs --total | awk 'END {print $2\",\"$3}'",
                    properties.getMetrics().getTimeoutMs()).getOutput());
            NetworkStats networkStats = resolveLinuxNetworkStats(
                    host.getServerId(),
                    client.executeCommand(LINUX_NETWORK_COUNTER_COMMAND, properties.getMetrics().getTimeoutMs()).getOutput());
            return ServerMetricsSnapshot.builder()
                    .serverId(host.getServerId())
                    .serverCode(host.getServerCode())
                    .status("ONLINE")
                    .online(Boolean.TRUE)
                    .latencyMs(latency)
                    .cpuUsage(round(cpuUsage))
                    .cpuCores(cpuCores)
                    .memoryUsage(round(memoryStats.usagePercent()))
                    .memoryTotalBytes(memoryStats.totalBytes())
                    .memoryUsedBytes(memoryStats.usedBytes())
                    .diskUsage(round(diskStats.usagePercent()))
                    .diskTotalBytes(diskStats.totalBytes())
                    .diskUsedBytes(diskStats.usedBytes())
                    .ioReadBytesPerSecond(round(networkStats.readBytesPerSecond()))
                    .ioWriteBytesPerSecond(round(networkStats.writeBytesPerSecond()))
                    .networkRxPacketsPerSecond(round(networkStats.readPacketsPerSecond()))
                    .networkTxPacketsPerSecond(round(networkStats.writePacketsPerSecond()))
                    .collectTimestamp(System.currentTimeMillis())
                    .detailMessage("SSH 状态已更新")
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    /**
     * 通过 WinRM 执行 PowerShell 采集远程 Windows 指标。
     */
    private ServerMetricsSnapshot collectViaWinRm(ServerHost host) throws Exception {
        WinRmExecClient client = new WinRmExecClient(toClientSetting(host, 5985));
        try {
            client.connect();
            long start = System.nanoTime();
            var result = client.executeCommand(
                    "powershell -NoProfile -Command \"$cpu=(Get-Counter '\\\\Processor(_Total)\\\\% Processor Time').CounterSamples.CookedValue;"
                            + "$system=Get-CimInstance Win32_ComputerSystem;"
                            + "$os=Get-CimInstance Win32_OperatingSystem;"
                            + "$memoryTotal=[int64]($os.TotalVisibleMemorySize * 1KB);"
                            + "$memoryUsed=[int64](($os.TotalVisibleMemorySize-$os.FreePhysicalMemory) * 1KB);"
                            + "$disks=Get-CimInstance Win32_LogicalDisk -Filter \\\"DriveType=3\\\";"
                            + "$diskTotal=[int64](($disks | Measure-Object -Property Size -Sum).Sum);"
                            + "$diskFree=[int64](($disks | Measure-Object -Property FreeSpace -Sum).Sum);"
                            + "$diskUsed=if ($diskTotal -gt 0) { $diskTotal - $diskFree } else { 0 };"
                            + "$network=Get-NetAdapterStatistics -ErrorAction SilentlyContinue;"
                            + "$ioRead=[double](($network | Measure-Object -Property ReceivedBytes -Sum).Sum);"
                            + "$ioWrite=[double](($network | Measure-Object -Property SentBytes -Sum).Sum);"
                            + "$networkRxPackets=0;"
                            + "$networkTxPackets=0;"
                            + "$network | ForEach-Object {"
                            + "$networkRxPackets += [double](($_.ReceivedUnicastPackets)+($_.ReceivedBroadcastPackets)+($_.ReceivedMulticastPackets));"
                            + "$networkTxPackets += [double](($_.SentUnicastPackets)+($_.SentBroadcastPackets)+($_.SentMulticastPackets));"
                            + "};"
                            + "Write-Output \\\"cpu=$([math]::Round($cpu,2))\\\";"
                            + "Write-Output \\\"cpuCores=$($system.NumberOfLogicalProcessors)\\\";"
                            + "Write-Output \\\"memoryTotal=$memoryTotal\\\";"
                            + "Write-Output \\\"memoryUsed=$memoryUsed\\\";"
                            + "Write-Output \\\"diskTotal=$diskTotal\\\";"
                            + "Write-Output \\\"diskUsed=$diskUsed\\\";"
                            + "Write-Output \\\"ioRead=$([math]::Round($ioRead,2))\\\";"
                            + "Write-Output \\\"ioWrite=$([math]::Round($ioWrite,2))\\\";"
                            + "Write-Output \\\"networkRxPackets=$([math]::Round($networkRxPackets,2))\\\";"
                            + "Write-Output \\\"networkTxPackets=$([math]::Round($networkTxPackets,2))\\\"\"",
                    properties.getMetrics().getTimeoutMs());
            int latency = (int) ((System.nanoTime() - start) / 1_000_000L);
            Map<String, String> values = parseKeyValues(result.getOutput());
            long memoryTotal = parseLong(values.get("memoryTotal"));
            long memoryUsed = resolveUsedBytes(memoryTotal, parseLong(values.get("memoryUsed")));
            long diskTotal = parseLong(values.get("diskTotal"));
            long diskUsed = resolveUsedBytes(diskTotal, parseLong(values.get("diskUsed")));
            NetworkStats networkStats = resolveWindowsNetworkStats(host.getServerId(), values);
            return ServerMetricsSnapshot.builder()
                    .serverId(host.getServerId())
                    .serverCode(host.getServerCode())
                    .status(result.isSuccess() ? "ONLINE" : "ERROR")
                    .online(result.isSuccess())
                    .latencyMs(latency)
                    .cpuUsage(round(parseDouble(values.get("cpu"))))
                    .cpuCores(parseInteger(values.get("cpuCores")))
                    .memoryUsage(round(resolveUsagePercent(memoryTotal, memoryUsed)))
                    .memoryTotalBytes(memoryTotal)
                    .memoryUsedBytes(memoryUsed)
                    .diskUsage(round(resolveUsagePercent(diskTotal, diskUsed)))
                    .diskTotalBytes(diskTotal)
                    .diskUsedBytes(diskUsed)
                    .ioReadBytesPerSecond(round(networkStats.readBytesPerSecond()))
                    .ioWriteBytesPerSecond(round(networkStats.writeBytesPerSecond()))
                    .networkRxPacketsPerSecond(round(networkStats.readPacketsPerSecond()))
                    .networkTxPacketsPerSecond(round(networkStats.writePacketsPerSecond()))
                    .collectTimestamp(System.currentTimeMillis())
                    .detailMessage(result.isSuccess() ? "WinRM 状态已更新" : result.getError())
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    /**
     * 为停用服务器生成占位快照，前端可直接展示停用态。
     */
    private ServerMetricsSnapshot buildDisabledSnapshot(ServerHost host) {
        return ServerMetricsSnapshot.builder()
                .serverId(host.getServerId())
                .serverCode(host.getServerCode())
                .status("DISABLED")
                .online(Boolean.FALSE)
                .collectTimestamp(System.currentTimeMillis())
                .detailMessage("服务器已停用")
                .build();
    }

    /**
     * 根据主机协议组装指标详情视图。
     */
    private ServerMetricsDetail collectDetail(ServerHost host, ServerMetricsSnapshot snapshot) {
        return switch (StringUtils.hasText(host.getServerType()) ? host.getServerType().toUpperCase() : "LOCAL") {
            case "SSH" -> collectUnixDetail(host, snapshot, false);
            case "WINRM" -> collectWindowsDetail(host, snapshot, false);
            default -> isWindowsHost(host)
                    ? collectWindowsDetail(host, snapshot, true)
                    : collectUnixDetail(host, snapshot, true);
        };
    }

    /**
     * 采集 Unix/Linux 主机的系统事实、磁盘分区与网卡明细。
     */
    private ServerMetricsDetail collectUnixDetail(ServerHost host, ServerMetricsSnapshot snapshot, boolean local) {
        String factsOutput = local
                ? executeLocalCommand("sh", "-c", UNIX_FACTS_COMMAND)
                : executeSshCommand(host, UNIX_FACTS_COMMAND);
        String diskOutput = local
                ? executeLocalCommand("sh", "-c", UNIX_DISK_COMMAND)
                : executeSshCommand(host, UNIX_DISK_COMMAND);
        String networkOutput = local
                ? executeLocalCommand("sh", "-c", UNIX_NETWORK_COMMAND)
                : executeSshCommand(host, UNIX_NETWORK_COMMAND);
        Map<String, String> facts = parseKeyValues(factsOutput);
        return ServerMetricsDetail.builder()
                .serverId(host.getServerId())
                .serverCode(host.getServerCode())
                .hostName(readFact(facts, "hostName"))
                .publicIp(readFact(facts, "publicIp"))
                .actualOsName(readFact(facts, "actualOsName"))
                .actualKernel(readFact(facts, "actualKernel"))
                .collectTimestamp(snapshot == null ? System.currentTimeMillis() : snapshot.getCollectTimestamp())
                .diskPartitions(parseUnixDiskPartitions(diskOutput))
                .networkInterfaces(parseUnixNetworkInterfaces(networkOutput))
                .build();
    }

    /**
     * 采集 Windows 主机的系统事实、磁盘分区与网卡明细。
     */
    private ServerMetricsDetail collectWindowsDetail(ServerHost host, ServerMetricsSnapshot snapshot, boolean local) {
        String factsOutput = local
                ? executeLocalCommand("powershell", "-NoProfile", "-Command", WINDOWS_FACTS_COMMAND)
                : executeWinRmCommand(host, WINDOWS_FACTS_COMMAND);
        String diskOutput = local
                ? executeLocalCommand("powershell", "-NoProfile", "-Command", WINDOWS_DISK_COMMAND)
                : executeWinRmCommand(host, WINDOWS_DISK_COMMAND);
        String networkOutput = local
                ? executeLocalCommand("powershell", "-NoProfile", "-Command", WINDOWS_NETWORK_COMMAND)
                : executeWinRmCommand(host, WINDOWS_NETWORK_COMMAND);
        Map<String, String> facts = parseKeyValues(factsOutput);
        return ServerMetricsDetail.builder()
                .serverId(host.getServerId())
                .serverCode(host.getServerCode())
                .hostName(readFact(facts, "hostName"))
                .publicIp(readFact(facts, "publicIp"))
                .actualOsName(readFact(facts, "actualOsName"))
                .actualKernel(readFact(facts, "actualKernel"))
                .collectTimestamp(snapshot == null ? System.currentTimeMillis() : snapshot.getCollectTimestamp())
                .diskPartitions(parseWindowsDiskPartitions(diskOutput))
                .networkInterfaces(parseWindowsNetworkInterfaces(networkOutput))
                .build();
    }

    /**
     * 首次读取时从 Redis 预热快照缓存，并同步恢复最近历史。
     */
    private void ensureSnapshotCacheLoaded() {
        if (!snapshotCache.isEmpty() || redisCacheLoaded) {
            return;
        }
        synchronized (this) {
            if (!snapshotCache.isEmpty() || redisCacheLoaded) {
                return;
            }
            redisCacheLoaded = true;
            StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
            if (redisTemplate == null || !properties.getMetrics().isCacheEnabled()) {
                return;
            }
            try {
                redisTemplate.opsForHash().entries(SNAPSHOT_CACHE_KEY).forEach((key, value) -> {
                    try {
                        ServerMetricsSnapshot snapshot = objectMapper.readValue(
                                String.valueOf(value),
                                ServerMetricsSnapshot.class);
                        if (snapshot != null && snapshot.getServerId() != null) {
                            snapshotCache.put(snapshot.getServerId(), snapshot);
                            recordHistory(snapshot);
                        }
                    } catch (Exception ignored) {
                        // ignore broken cache entries
                    }
                });
            } catch (Exception ignored) {
                // ignore cache bootstrap failures
            }
        }
    }

    /**
     * 把最新快照回写到 Redis，供页面刷新和重启恢复复用。
     */
    private void cacheSnapshot(ServerMetricsSnapshot snapshot) {
        if (snapshot == null || snapshot.getServerId() == null || !properties.getMetrics().isCacheEnabled()) {
            return;
        }
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(
                    SNAPSHOT_CACHE_KEY,
                    String.valueOf(snapshot.getServerId()),
                    objectMapper.writeValueAsString(snapshot));
            long ttl = Math.max(0L, properties.getMetrics().getCacheTtlSeconds());
            if (ttl > 0L) {
                redisTemplate.expire(SNAPSHOT_CACHE_KEY, Duration.ofSeconds(ttl));
            }
        } catch (Exception ignored) {
            // ignore cache write failures
        }
    }

    /**
     * 在内存中追加历史点位，并异步持久化到历史表。
     */
    private void recordHistory(ServerMetricsSnapshot snapshot) {
        if (snapshot == null || snapshot.getServerId() == null) {
            return;
        }
        Deque<ServerMetricsSnapshot> history = historyCache.computeIfAbsent(
                snapshot.getServerId(),
                key -> new ConcurrentLinkedDeque<>());
        history.addLast(cloneSnapshot(snapshot));
        while (history.size() > MAX_HISTORY_POINTS) {
            history.pollFirst();
        }
        persistHistory(snapshot);
    }

    /**
     * 异步落库存档历史指标，避免阻塞实时采集线程。
     */
    @Async
    protected void persistHistory(ServerMetricsSnapshot snapshot) {
        if (snapshot == null || snapshot.getServerId() == null) {
            return;
        }
        try {
            ServerMetricsHistory entity = new ServerMetricsHistory();
            entity.setServerId(snapshot.getServerId());
            entity.setServerCode(snapshot.getServerCode());
            entity.setStatus(snapshot.getStatus());
            entity.setOnline(snapshot.getOnline());
            entity.setLatencyMs(snapshot.getLatencyMs());
            entity.setCpuUsage(snapshot.getCpuUsage());
            entity.setCpuCores(snapshot.getCpuCores());
            entity.setMemoryUsage(snapshot.getMemoryUsage());
            entity.setMemoryTotalBytes(snapshot.getMemoryTotalBytes());
            entity.setMemoryUsedBytes(snapshot.getMemoryUsedBytes());
            entity.setDiskUsage(snapshot.getDiskUsage());
            entity.setDiskTotalBytes(snapshot.getDiskTotalBytes());
            entity.setDiskUsedBytes(snapshot.getDiskUsedBytes());
            entity.setIoReadBytesPerSecond(snapshot.getIoReadBytesPerSecond());
            entity.setIoWriteBytesPerSecond(snapshot.getIoWriteBytesPerSecond());
            entity.setNetworkRxPacketsPerSecond(snapshot.getNetworkRxPacketsPerSecond());
            entity.setNetworkTxPacketsPerSecond(snapshot.getNetworkTxPacketsPerSecond());
            entity.setCollectTimestamp(snapshot.getCollectTimestamp());
            entity.setDetailMessage(snapshot.getDetailMessage());
            serverMetricsHistoryMapper.insert(entity);
        } catch (Exception ignored) {
            // ignore history persistence failures to avoid blocking realtime collection
        }
    }

    /**
     * 从历史表读取指定时间范围内的指标点位，并按时间升序返回。
     */
    private List<ServerMetricsSnapshot> listPersistedHistory(Integer serverId, Integer minutes) {
        long cutoff = resolveHistoryCutoff(minutes);
        List<ServerMetricsHistory> rows = serverMetricsHistoryMapper.selectList(
                Wrappers.<ServerMetricsHistory>lambdaQuery()
                        .eq(ServerMetricsHistory::getServerId, serverId)
                        .ge(cutoff > 0L, ServerMetricsHistory::getCollectTimestamp, cutoff)
                        .orderByDesc(ServerMetricsHistory::getCollectTimestamp, ServerMetricsHistory::getServerMetricsHistoryId)
                        .last("limit " + MAX_HISTORY_POINTS));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ServerMetricsSnapshot> snapshots = new ArrayList<>(rows.size());
        for (int index = rows.size() - 1; index >= 0; index -= 1) {
            snapshots.add(toSnapshot(rows.get(index)));
        }
        return snapshots;
    }

    /**
     * 把历史表实体还原成前端使用的指标快照结构。
     */
    private ServerMetricsSnapshot toSnapshot(ServerMetricsHistory row) {
        return ServerMetricsSnapshot.builder()
                .serverId(row.getServerId())
                .serverCode(row.getServerCode())
                .status(row.getStatus())
                .online(row.getOnline())
                .latencyMs(row.getLatencyMs())
                .cpuUsage(row.getCpuUsage())
                .cpuCores(row.getCpuCores())
                .memoryUsage(row.getMemoryUsage())
                .memoryTotalBytes(row.getMemoryTotalBytes())
                .memoryUsedBytes(row.getMemoryUsedBytes())
                .diskUsage(row.getDiskUsage())
                .diskTotalBytes(row.getDiskTotalBytes())
                .diskUsedBytes(row.getDiskUsedBytes())
                .ioReadBytesPerSecond(row.getIoReadBytesPerSecond())
                .ioWriteBytesPerSecond(row.getIoWriteBytesPerSecond())
                .networkRxPacketsPerSecond(row.getNetworkRxPacketsPerSecond())
                .networkTxPacketsPerSecond(row.getNetworkTxPacketsPerSecond())
                .collectTimestamp(row.getCollectTimestamp())
                .detailMessage(row.getDetailMessage())
                .build();
    }

    /**
     * 把分钟参数换算成历史查询的时间下界。
     */
    private long resolveHistoryCutoff(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return 0L;
        }
        return System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes);
    }

    /**
     * 复制快照对象，避免缓存中的可变引用被后续修改。
     */
    private ServerMetricsSnapshot cloneSnapshot(ServerMetricsSnapshot snapshot) {
        return ServerMetricsSnapshot.builder()
                .serverId(snapshot.getServerId())
                .serverCode(snapshot.getServerCode())
                .status(snapshot.getStatus())
                .online(snapshot.getOnline())
                .latencyMs(snapshot.getLatencyMs())
                .cpuUsage(snapshot.getCpuUsage())
                .cpuCores(snapshot.getCpuCores())
                .memoryUsage(snapshot.getMemoryUsage())
                .memoryTotalBytes(snapshot.getMemoryTotalBytes())
                .memoryUsedBytes(snapshot.getMemoryUsedBytes())
                .diskUsage(snapshot.getDiskUsage())
                .diskTotalBytes(snapshot.getDiskTotalBytes())
                .diskUsedBytes(snapshot.getDiskUsedBytes())
                .ioReadBytesPerSecond(snapshot.getIoReadBytesPerSecond())
                .ioWriteBytesPerSecond(snapshot.getIoWriteBytesPerSecond())
                .networkRxPacketsPerSecond(snapshot.getNetworkRxPacketsPerSecond())
                .networkTxPacketsPerSecond(snapshot.getNetworkTxPacketsPerSecond())
                .collectTimestamp(snapshot.getCollectTimestamp())
                .detailMessage(snapshot.getDetailMessage())
                .build();
    }

    /**
     * 汇总本机所有磁盘分区的总量与已用量。
     */
    private UsageStats resolveLocalDiskStats() {
        File[] roots = File.listRoots();
        if (roots == null) {
            return new UsageStats(0L, 0L);
        }
        long totalBytes = 0L;
        long usedBytes = 0L;
        for (File root : roots) {
            long total = root.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            totalBytes += total;
            usedBytes += resolveUsedBytes(total, total - root.getFreeSpace());
        }
        return new UsageStats(totalBytes, usedBytes);
    }

    /**
     * 本机优先使用 Oshi 字节计数，包计数则回落到平台脚本输出。
     */
    private NetworkStats resolveLocalNetworkStats(ServerHost host) {
        if (host == null) {
            return NetworkStats.empty();
        }
        long[] packetCounters = isWindowsHost(host)
                ? parseWindowsNetworkCounters(parseKeyValues(executeLocalCommand(
                "powershell",
                "-NoProfile",
                "-Command",
                WINDOWS_NETWORK_COUNTER_COMMAND)))
                : parseNetworkCounters(executeLocalCommand("sh", "-c", LINUX_NETWORK_COUNTER_COMMAND));
        NetworkByteCounters localBytes = resolveLocalOshiNetworkCounters();
        long readBytes = localBytes.available() ? localBytes.readBytes() : packetCounters[0];
        long writeBytes = localBytes.available() ? localBytes.writeBytes() : packetCounters[1];
        return resolveNetworkStats(
                host.getServerId(),
                readBytes,
                writeBytes,
                packetCounters[2],
                packetCounters[3]);
    }

    /**
     * 解析 Linux 采集脚本输出的网卡累计计数。
     */
    private NetworkStats resolveLinuxNetworkStats(Integer serverId, String output) {
        long[] values = parseNetworkCounters(output);
        return resolveNetworkStats(serverId, values[0], values[1], values[2], values[3]);
    }

    /**
     * 解析 Windows 采集输出并换算为实时吞吐速率。
     */
    private NetworkStats resolveWindowsNetworkStats(Integer serverId, Map<String, String> values) {
        long[] counters = parseWindowsNetworkCounters(values);
        return resolveNetworkStats(
                serverId,
                counters[0],
                counters[1],
                counters[2],
                counters[3]);
    }

    /**
     * 从 Windows 键值对结果中提取字节与包累计值。
     */
    private long[] parseWindowsNetworkCounters(Map<String, String> values) {
        return new long[]{
                Math.round(parseDouble(values.get("ioRead"))),
                Math.round(parseDouble(values.get("ioWrite"))),
                Math.round(parseDouble(values.get("networkRxPackets"))),
                Math.round(parseDouble(values.get("networkTxPackets")))};
    }

    /**
     * 用 Oshi 读取本机网卡字节累计值，补足脚本计数不稳定场景。
     */
    private NetworkByteCounters resolveLocalOshiNetworkCounters() {
        try {
            List<Network> networks = Oshi.newNetwork();
            if (networks == null || networks.isEmpty()) {
                return NetworkByteCounters.empty();
            }
            long readBytes = 0L;
            long writeBytes = 0L;
            for (Network network : networks) {
                if (network == null || shouldSkipLocalNetwork(network)) {
                    continue;
                }
                readBytes += Math.max(0L, network.getReceiveBytes());
                writeBytes += Math.max(0L, network.getTransmitBytes());
            }
            if (readBytes <= 0L && writeBytes <= 0L) {
                return NetworkByteCounters.empty();
            }
            return new NetworkByteCounters(readBytes, writeBytes, true);
        } catch (Throwable ignored) {
            return NetworkByteCounters.empty();
        }
    }

    /**
     * 过滤环回和虚拟回环网卡，避免网络速率被噪声污染。
     */
    private boolean shouldSkipLocalNetwork(Network network) {
        String name = ((network.getName() == null ? "" : network.getName())
                + " "
                + (network.getDisplayName() == null ? "" : network.getDisplayName()))
                .toLowerCase();
        return "lo".equals(name.trim())
                || name.contains("loopback")
                || name.contains("npcap loopback");
    }

    /**
     * 根据累计计数与上次采样差值计算每秒速率。
     */
    private NetworkStats resolveNetworkStats(
            Integer serverId,
            long readBytes,
            long writeBytes,
            long readPackets,
            long writePackets
    ) {
        if (serverId == null) {
            return NetworkStats.empty();
        }
        long now = System.currentTimeMillis();
        NetworkCounterState previous = networkCounterCache.put(
                serverId,
                new NetworkCounterState(now, readBytes, writeBytes, readPackets, writePackets));
        if (previous == null || now <= previous.timestampMillis()) {
            return NetworkStats.empty();
        }
        double seconds = (now - previous.timestampMillis()) / 1000D;
        if (seconds <= 0D) {
            return NetworkStats.empty();
        }
        return new NetworkStats(
                Math.max(0D, (readBytes - previous.readBytes()) / seconds),
                Math.max(0D, (writeBytes - previous.writeBytes()) / seconds),
                Math.max(0D, (readPackets - previous.readPackets()) / seconds),
                Math.max(0D, (writePackets - previous.writePackets()) / seconds));
    }

    /**
     * 解析通用的四段式网络累计值输出。
     */
    private long[] parseNetworkCounters(String output) {
        String[] parts = StringUtils.hasText(output) ? output.trim().split(",") : new String[0];
        long readBytes = parts.length > 0 ? parseLong(parts[0]) : 0L;
        long writeBytes = parts.length > 1 ? parseLong(parts[1]) : 0L;
        long readPackets = parts.length > 2 ? parseLong(parts[2]) : 0L;
        long writePackets = parts.length > 3 ? parseLong(parts[3]) : 0L;
        return new long[]{
                Math.max(readBytes, 0L),
                Math.max(writeBytes, 0L),
                Math.max(readPackets, 0L),
                Math.max(writePackets, 0L)};
    }

    /**
     * 根据主机元数据判断是否按 Windows 协议和命令集处理。
     */
    private boolean isWindowsHost(ServerHost host) {
        return host != null && ("WINDOWS".equalsIgnoreCase(host.getOsType())
                || "WINRM".equalsIgnoreCase(host.getServerType()));
    }

    /**
     * 执行 SSH 文本命令，异常时返回空串避免影响详情弹框展示。
     */
    private String executeSshCommand(ServerHost host, String command) {
        LinuxExecClient client = new LinuxExecClient(toClientSetting(host, 22));
        try {
            client.connect();
            return client.executeCommand(command, properties.getMetrics().getTimeoutMs()).getOutput();
        } catch (Exception ignored) {
            return "";
        } finally {
            client.closeQuietly();
        }
    }

    /**
     * 执行 WinRM PowerShell 命令并返回标准输出文本。
     */
    private String executeWinRmCommand(ServerHost host, String command) {
        WinRmExecClient client = new WinRmExecClient(toClientSetting(host, 5985));
        try {
            client.connect();
            return client.executeCommand(
                    "powershell -NoProfile -Command \"" + command.replace("\"", "\\\"") + "\"",
                    properties.getMetrics().getTimeoutMs()).getOutput();
        } catch (Exception ignored) {
            return "";
        } finally {
            client.closeQuietly();
        }
    }

    /**
     * 使用可达性探测估算本机到目标地址的基础时延。
     */
    private int resolveLocalLatency(ServerHost host) {
        try {
            long start = System.nanoTime();
            InetAddress.getByName(StringUtils.hasText(host.getHost()) ? host.getHost() : "127.0.0.1")
                    .isReachable(Math.min(properties.getMetrics().getTimeoutMs(), 1500));
            return (int) ((System.nanoTime() - start) / 1_000_000L);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * 执行本地 shell 命令，超时则强制回收进程。
     */
    private String executeLocalCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(properties.getMetrics().getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            try (InputStream inputStream = process.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 把服务器配置映射为 SSH/WinRM 客户端连接参数。
     */
    private ClientSetting toClientSetting(ServerHost host, int defaultPort) {
        return ClientSetting.builder()
                .host("LOCAL".equalsIgnoreCase(host.getServerType()) ? "127.0.0.1" : host.getHost())
                .port(host.getPort() == null ? defaultPort : host.getPort())
                .username(host.getUsername())
                .password(host.getPassword())
                .build();
    }

    /**
     * 解析 total,used 格式的容量输出。
     */
    private UsageStats parseUsageStats(String output) {
        String[] parts = StringUtils.hasText(output) ? output.trim().split(",") : new String[0];
        long totalBytes = parts.length > 0 ? parseLong(parts[0]) : 0L;
        long usedBytes = parts.length > 1 ? resolveUsedBytes(totalBytes, parseLong(parts[1])) : 0L;
        return new UsageStats(totalBytes, usedBytes);
    }

    /**
     * 把文本安全转换为 double，异常时回落到 0。
     */
    private double parseDouble(String output) {
        if (!StringUtils.hasText(output)) {
            return 0D;
        }
        try {
            return Double.parseDouble(output.trim());
        } catch (Exception ignored) {
            return 0D;
        }
    }

    /**
     * 把文本安全转换为非负整数。
     */
    private int parseInteger(String output) {
        return (int) Math.max(0L, parseLong(output));
    }

    /**
     * 把文本安全转换为 long，异常时回落到 0。
     */
    private long parseLong(String output) {
        if (!StringUtils.hasText(output)) {
            return 0L;
        }
        try {
            return Long.parseLong(output.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /**
     * 解析多行 key=value 文本输出。
     */
    private Map<String, String> parseKeyValues(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        for (String line : text.split("\\R")) {
            int index = line.indexOf('=');
            if (index <= 0) {
                continue;
            }
            result.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
        }
        return result;
    }

    /**
     * 读取系统事实字段并把空串归一化为 null。
     */
    private String readFact(Map<String, String> facts, String key) {
        String value = facts.getOrDefault(key, "");
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 把 df 输出转换为磁盘分区视图。
     */
    private List<ServerDiskPartitionView> parseUnixDiskPartitions(String output) {
        if (!StringUtils.hasText(output)) {
            return Collections.emptyList();
        }
        List<ServerDiskPartitionView> items = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 6) {
                continue;
            }
            long totalBytes = parseLong(parts[1]);
            long usedBytes = resolveUsedBytes(totalBytes, parseLong(parts[2]));
            long freeBytes = Math.max(0L, parseLong(parts[3]));
            items.add(ServerDiskPartitionView.builder()
                    .name(parts[0])
                    .mountPoint(parts[5])
                    .fileSystem(parts[0])
                    .label(parts[5])
                    .totalBytes(totalBytes)
                    .usedBytes(usedBytes)
                    .freeBytes(freeBytes)
                    .usagePercent(round(resolveUsagePercent(totalBytes, usedBytes)))
                    .status("mounted")
                    .build());
        }
        return items;
    }

    /**
     * 把 Unix 网卡脚本输出转换为网卡明细视图。
     */
    private List<ServerNetworkInterfaceView> parseUnixNetworkInterfaces(String output) {
        if (!StringUtils.hasText(output)) {
            return Collections.emptyList();
        }
        List<ServerNetworkInterfaceView> items = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            Map<String, String> values = parsePipeKeyValues(line);
            String name = values.get("iface");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            items.add(ServerNetworkInterfaceView.builder()
                    .name(name)
                    .displayName(name)
                    .status(values.get("state"))
                    .ipv4(values.get("ipv4"))
                    .macAddress(values.get("mac"))
                    .receivedBytes(parseLong(values.get("rx")))
                    .transmittedBytes(parseLong(values.get("tx")))
                    .receivedPackets(parseLong(values.get("rxp")))
                    .transmittedPackets(parseLong(values.get("txp")))
                    .build());
        }
        return items;
    }

    /**
     * 解析 Windows JSON 磁盘列表。
     */
    private List<ServerDiskPartitionView> parseWindowsDiskPartitions(String output) {
        return readJsonList(output, node -> ServerDiskPartitionView.builder()
                .name(readJsonText(node, "name"))
                .mountPoint(readJsonText(node, "mountPoint"))
                .fileSystem(readJsonText(node, "fileSystem"))
                .label(readJsonText(node, "label"))
                .totalBytes(readJsonLong(node, "totalBytes"))
                .usedBytes(readJsonLong(node, "usedBytes"))
                .freeBytes(readJsonLong(node, "freeBytes"))
                .usagePercent(readJsonDouble(node, "usagePercent"))
                .status(readJsonText(node, "status"))
                .build());
    }

    /**
     * 解析 Windows JSON 网卡列表。
     */
    private List<ServerNetworkInterfaceView> parseWindowsNetworkInterfaces(String output) {
        return readJsonList(output, node -> ServerNetworkInterfaceView.builder()
                .name(readJsonText(node, "name"))
                .displayName(readJsonText(node, "displayName"))
                .status(readJsonText(node, "status"))
                .ipv4(readJsonText(node, "ipv4"))
                .macAddress(readJsonText(node, "macAddress"))
                .receivedBytes(readJsonLong(node, "receivedBytes"))
                .transmittedBytes(readJsonLong(node, "transmittedBytes"))
                .receivedPackets(readJsonLong(node, "receivedPackets"))
                .transmittedPackets(readJsonLong(node, "transmittedPackets"))
                .build());
    }

    /**
     * 解析单行以 | 分隔的 key=value 输出。
     */
    private Map<String, String> parsePipeKeyValues(String line) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!StringUtils.hasText(line)) {
            return values;
        }
        for (String item : line.split("\\|")) {
            int index = item.indexOf('=');
            if (index <= 0) {
                continue;
            }
            values.put(item.substring(0, index).trim(), item.substring(index + 1).trim());
        }
        return values;
    }

    /**
     * 安全读取 JSON 数组或单对象，统一映射成列表结果。
     */
    private <T> List<T> readJsonList(String output, JsonNodeReader<T> reader) {
        if (!StringUtils.hasText(output)) {
            return Collections.emptyList();
        }
        try {
            var node = objectMapper.readTree(output.trim());
            if (node == null || node.isNull()) {
                return Collections.emptyList();
            }
            List<T> result = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(item -> {
                    T value = reader.read(item);
                    if (value != null) {
                        result.add(value);
                    }
                });
                return result;
            }
            T value = reader.read(node);
            return value == null ? Collections.emptyList() : List.of(value);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * 读取 JSON 文本字段并做空串归一化。
     */
    private String readJsonText(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 读取 JSON long 字段。
     */
    private Long readJsonLong(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return 0L;
        }
        return node.get(field).asLong(0L);
    }

    /**
     * 读取 JSON double 字段并统一保留两位小数。
     */
    private Double readJsonDouble(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return 0D;
        }
        return round(node.get(field).asDouble(0D));
    }

    /**
     * 把已用容量限制在 0 到总量之间。
     */
    private long resolveUsedBytes(long totalBytes, long usedBytes) {
        if (totalBytes <= 0) {
            return 0L;
        }
        return Math.max(0L, Math.min(totalBytes, usedBytes));
    }

    /**
     * 根据总量与已用量计算百分比。
     */
    private double resolveUsagePercent(long totalBytes, long usedBytes) {
        if (totalBytes <= 0) {
            return 0D;
        }
        return ((double) resolveUsedBytes(totalBytes, usedBytes) / totalBytes) * 100D;
    }

    /**
     * 指标统一保留两位小数，便于前端直接展示。
     */
    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    @FunctionalInterface
    private interface JsonNodeReader<T> {
        /**
         * 把单个 JSON 节点转换成目标模型。
         */
        T read(com.fasterxml.jackson.databind.JsonNode node);
    }

    private record NetworkStats(
            double readBytesPerSecond,
            double writeBytesPerSecond,
            double readPacketsPerSecond,
            double writePacketsPerSecond
    ) {
        /**
         * 返回全零占位结果，表示尚未形成有效采样间隔。
         */
        private static NetworkStats empty() {
            return new NetworkStats(0D, 0D, 0D, 0D);
        }
    }

    private record NetworkCounterState(
            long timestampMillis,
            long readBytes,
            long writeBytes,
            long readPackets,
            long writePackets
    ) {
    }

    private record NetworkByteCounters(
            long readBytes,
            long writeBytes,
            boolean available
    ) {
        /**
         * 返回不可用占位值，提示上层回落到脚本计数。
         */
        private static NetworkByteCounters empty() {
            return new NetworkByteCounters(0L, 0L, false);
        }
    }

    private record UsageStats(long totalBytes, long usedBytes) {
        /**
         * 计算容量使用率，供快照和分区视图复用。
         */
        private double usagePercent() {
            if (totalBytes <= 0) {
                return 0D;
            }
            return ((double) usedBytes / totalBytes) * 100D;
        }
    }

    private record MetricsRefreshJobState(
            String schedulerMode,
            Boolean jobEnabled,
            Integer jobId,
            String jobNo,
            String jobName,
            String jobScheduleType,
            String jobScheduleTime,
            String jobStatus,
            Long lastTriggerAt,
            Long nextTriggerAt
    ) {
    }
}
