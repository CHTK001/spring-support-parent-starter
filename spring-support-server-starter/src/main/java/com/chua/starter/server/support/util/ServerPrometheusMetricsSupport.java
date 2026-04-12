package com.chua.starter.server.support.util;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerDiskPartitionView;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.model.ServerNetworkInterfaceView;
import com.chua.starter.server.support.spi.ServerCommandExecutorSpi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.util.StringUtils;

/**
 * 统一处理 Prometheus 指标查询，供指标 SPI 与历史查询共用。
 */
public final class ServerPrometheusMetricsSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final com.chua.common.support.core.spi.ServiceProvider<ServerCommandExecutorSpi> COMMAND_EXECUTOR_PROVIDER =
            ServiceProvider.of(ServerCommandExecutorSpi.class);
    private static final String DISK_FILTER =
            "fstype!~\"tmpfs|overlay|squashfs|rootfs|nsfs\",mountpoint!~\"/(proc|sys|dev|run)($|/)\"";

    private ServerPrometheusMetricsSupport() {
    }

    /**
     * 通过 Prometheus HTTP API 采集单机快照。
     */
    public static ServerMetricsSnapshot collectSnapshot(ServerHost host) throws Exception {
        String baseUrl = requireBaseUrl(host);
        String matcher = instanceMatcher(host);
        String time = String.valueOf(System.currentTimeMillis() / 1000.0d);
        int lookbackSeconds = ServerHostMetadataSupport.resolvePrometheusLookbackSeconds(host, 120);
        String rangeWindow = "[" + Math.max(lookbackSeconds, 30) + "s]";
        Double up = queryScalar(host, baseUrl, "max(up{instance=~\"" + matcher + "\"})", time);
        Double cpuUsage = queryScalar(host, baseUrl,
                "100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\",instance=~\"" + matcher + "\"}" + rangeWindow + ")) * 100)",
                time);
        Double cpuCores = queryScalar(host, baseUrl, "max(machine_cpu_cores{instance=~\"" + matcher + "\"})", time);
        if (cpuCores == null) {
            cpuCores = queryScalar(host, baseUrl,
                    "count(count by (cpu) (node_cpu_seconds_total{mode=\"idle\",instance=~\"" + matcher + "\"}))",
                    time);
        }
        Double memoryTotal = queryScalar(host, baseUrl, "max(node_memory_MemTotal_bytes{instance=~\"" + matcher + "\"})", time);
        Double memoryAvailable = queryScalar(host, baseUrl,
                "max(node_memory_MemAvailable_bytes{instance=~\"" + matcher + "\"})",
                time);
        Double diskTotal = queryScalar(host, baseUrl,
                "sum(node_filesystem_size_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "})",
                time);
        Double diskAvailable = queryScalar(host, baseUrl,
                "sum(node_filesystem_avail_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "})",
                time);
        Double ioRead = queryScalar(host, baseUrl,
                "sum(rate(node_network_receive_bytes_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                time);
        Double ioWrite = queryScalar(host, baseUrl,
                "sum(rate(node_network_transmit_bytes_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                time);
        Double rxPackets = queryScalar(host, baseUrl,
                "sum(rate(node_network_receive_packets_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                time);
        Double txPackets = queryScalar(host, baseUrl,
                "sum(rate(node_network_transmit_packets_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                time);
        long memoryTotalBytes = asLong(memoryTotal);
        long memoryUsedBytes = Math.max(memoryTotalBytes - asLong(memoryAvailable), 0L);
        long diskTotalBytes = asLong(diskTotal);
        long diskUsedBytes = Math.max(diskTotalBytes - asLong(diskAvailable), 0L);
        boolean online = (up != null && up > 0D)
                || cpuUsage != null
                || memoryTotal != null
                || diskTotal != null;
        return ServerMetricsSnapshot.builder()
                .serverId(host == null ? null : host.getServerId())
                .serverCode(host == null ? null : host.getServerCode())
                .status(online ? "ONLINE" : "OFFLINE")
                .online(online)
                .latencyMs(null)
                .cpuUsage(scale2(cpuUsage))
                .cpuCores(cpuCores == null ? null : Math.max(cpuCores.intValue(), 0))
                .memoryUsage(percent(memoryUsedBytes, memoryTotalBytes))
                .memoryTotalBytes(memoryTotalBytes)
                .memoryUsedBytes(memoryUsedBytes)
                .diskUsage(percent(diskUsedBytes, diskTotalBytes))
                .diskTotalBytes(diskTotalBytes)
                .diskUsedBytes(diskUsedBytes)
                .ioReadBytesPerSecond(scale2(ioRead))
                .ioWriteBytesPerSecond(scale2(ioWrite))
                .networkRxPacketsPerSecond(scale2(rxPackets))
                .networkTxPacketsPerSecond(scale2(txPackets))
                .collectTimestamp(System.currentTimeMillis())
                .detailMessage("Prometheus 状态已更新")
                .build();
    }

    /**
     * 通过 Prometheus 构造磁盘和网卡详情视图。
     */
    public static ServerMetricsDetail collectDetail(ServerHost host, ServerMetricsSnapshot snapshot) throws Exception {
        String baseUrl = requireBaseUrl(host);
        String matcher = instanceMatcher(host);
        String time = String.valueOf(System.currentTimeMillis() / 1000.0d);
        JsonNode unameSeries = queryVector(host, baseUrl, "node_uname_info{instance=~\"" + matcher + "\"}", time);
        String hostName = firstMetricLabel(unameSeries, "nodename", "instance");
        String sysName = firstMetricLabel(unameSeries, "sysname");
        String release = firstMetricLabel(unameSeries, "release");
        String machine = firstMetricLabel(unameSeries, "machine");
        Map<String, DiskAccumulator> disks = new LinkedHashMap<>();
        mergeDiskSeries(disks, queryVector(host, baseUrl,
                "node_filesystem_size_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "}",
                time), "total");
        mergeDiskSeries(disks, queryVector(host, baseUrl,
                "node_filesystem_avail_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "}",
                time), "free");
        Map<String, NetworkAccumulator> networks = new LinkedHashMap<>();
        mergeNetworkSeries(networks, queryVector(host, baseUrl,
                "node_network_receive_bytes_total{instance=~\"" + matcher + "\",device!=\"lo\"}",
                time), "rxBytes");
        mergeNetworkSeries(networks, queryVector(host, baseUrl,
                "node_network_transmit_bytes_total{instance=~\"" + matcher + "\",device!=\"lo\"}",
                time), "txBytes");
        mergeNetworkSeries(networks, queryVector(host, baseUrl,
                "node_network_receive_packets_total{instance=~\"" + matcher + "\",device!=\"lo\"}",
                time), "rxPackets");
        mergeNetworkSeries(networks, queryVector(host, baseUrl,
                "node_network_transmit_packets_total{instance=~\"" + matcher + "\",device!=\"lo\"}",
                time), "txPackets");
        mergeNetworkSeries(networks, queryVector(host, baseUrl,
                "node_network_up{instance=~\"" + matcher + "\",device!=\"lo\"}",
                time), "status");
        List<ServerDiskPartitionView> diskViews = disks.values().stream()
                .map(DiskAccumulator::toView)
                .sorted(Comparator.comparing(ServerDiskPartitionView::getMountPoint, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<ServerNetworkInterfaceView> networkViews = networks.values().stream()
                .map(NetworkAccumulator::toView)
                .sorted(Comparator.comparing(ServerNetworkInterfaceView::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return ServerMetricsDetail.builder()
                .serverId(host == null ? null : host.getServerId())
                .serverCode(host == null ? null : host.getServerCode())
                .hostName(StringUtils.hasText(hostName) ? hostName : host == null ? null : host.getServerName())
                .publicIp(host == null ? null : host.getHost())
                .actualOsName(joinNonBlank(" ", sysName, release, machine))
                .actualKernel(joinNonBlank(" ", sysName, release))
                .collectTimestamp(snapshot == null ? System.currentTimeMillis() : snapshot.getCollectTimestamp())
                .diskPartitions(diskViews)
                .networkInterfaces(networkViews)
                .build();
    }

    /**
     * 通过 query_range 直接从 Prometheus 读取历史快照，不落本地表。
     */
    public static List<ServerMetricsSnapshot> listHistory(ServerHost host, Integer minutes) throws Exception {
        String baseUrl = requireBaseUrl(host);
        String matcher = instanceMatcher(host);
        long endMillis = System.currentTimeMillis();
        long startMillis = minutes == null || minutes <= 0
                ? endMillis - Duration.ofHours(1).toMillis()
                : endMillis - Duration.ofMinutes(minutes).toMillis();
        int stepSeconds = Math.max(ServerHostMetadataSupport.resolvePrometheusStepSeconds(host, 60), 15);
        int lookbackSeconds = ServerHostMetadataSupport.resolvePrometheusLookbackSeconds(host, 120);
        String rangeWindow = "[" + Math.max(lookbackSeconds, 30) + "s]";
        NavigableMap<Long, SnapshotBuilder> timeline = new TreeMap<>();
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\",instance=~\"" + matcher + "\"}" + rangeWindow + ")) * 100)",
                startMillis, endMillis, stepSeconds), "cpuUsage");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "max(node_memory_MemTotal_bytes{instance=~\"" + matcher + "\"}) - max(node_memory_MemAvailable_bytes{instance=~\"" + matcher + "\"})",
                startMillis, endMillis, stepSeconds), "memoryUsed");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "max(node_memory_MemTotal_bytes{instance=~\"" + matcher + "\"})",
                startMillis, endMillis, stepSeconds), "memoryTotal");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "sum(node_filesystem_size_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "}) - sum(node_filesystem_avail_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "})",
                startMillis, endMillis, stepSeconds), "diskUsed");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "sum(node_filesystem_size_bytes{instance=~\"" + matcher + "\"," + DISK_FILTER + "})",
                startMillis, endMillis, stepSeconds), "diskTotal");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "sum(rate(node_network_receive_bytes_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                startMillis, endMillis, stepSeconds), "ioRead");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "sum(rate(node_network_transmit_bytes_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                startMillis, endMillis, stepSeconds), "ioWrite");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "sum(rate(node_network_receive_packets_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                startMillis, endMillis, stepSeconds), "rxPackets");
        mergeHistorySeries(timeline, queryRange(host, baseUrl,
                "sum(rate(node_network_transmit_packets_total{instance=~\"" + matcher + "\",device!=\"lo\"}" + rangeWindow + "))",
                startMillis, endMillis, stepSeconds), "txPackets");
        Double cpuCores = queryScalar(host, baseUrl, "max(machine_cpu_cores{instance=~\"" + matcher + "\"})", null);
        List<ServerMetricsSnapshot> snapshots = new ArrayList<>(timeline.size());
        for (SnapshotBuilder builder : timeline.values()) {
            snapshots.add(builder.toSnapshot(host, cpuCores == null ? null : cpuCores.intValue()));
        }
        return snapshots;
    }

    /**
     * 读取 Prometheus 标量结果。
     */
    private static Double queryScalar(ServerHost host, String baseUrl, String promql, String time) throws Exception {
        JsonNode result = queryVector(host, baseUrl, promql, time);
        if (result == null || !result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode valueNode = result.get(0).path("value");
        return parsePrometheusValue(valueNode.path(1));
    }

    /**
     * 执行 instant query 并返回 vector 结果数组。
     */
    private static JsonNode queryVector(ServerHost host, String baseUrl, String promql, String time) throws Exception {
        StringBuilder uri = new StringBuilder(baseUrl)
                .append("/api/v1/query?query=")
                .append(urlEncode(promql));
        if (StringUtils.hasText(time)) {
            uri.append("&time=").append(urlEncode(time));
        }
        JsonNode root = execute(host, uri.toString());
        validateSuccess(root, promql);
        return root.path("data").path("result");
    }

    /**
     * 执行 range query 并返回 matrix 结果数组。
     */
    private static JsonNode queryRange(ServerHost host, String baseUrl, String promql, long startMillis, long endMillis, int stepSeconds)
            throws Exception {
        String uri = baseUrl + "/api/v1/query_range?query=" + urlEncode(promql)
                + "&start=" + urlEncode(String.valueOf(startMillis / 1000.0d))
                + "&end=" + urlEncode(String.valueOf(endMillis / 1000.0d))
                + "&step=" + urlEncode(stepSeconds + "s");
        JsonNode root = execute(host, uri);
        validateSuccess(root, promql);
        return root.path("data").path("result");
    }

    /**
     * 发起 HTTP 请求并把 JSON 反序列化为树结构。
     */
    private static JsonNode execute(ServerHost host, String uri) throws Exception {
        if (ServerHostMetadataSupport.resolvePrometheusViaSpi(host)) {
            return executeViaSpi(host, uri);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Prometheus 请求失败: HTTP " + response.statusCode());
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    /**
     * 通过服务器命令执行 SPI 在远端本机回环网络里访问 Prometheus。
     */
    private static JsonNode executeViaSpi(ServerHost host, String uri) throws Exception {
        ServerCommandExecutorSpi executor = COMMAND_EXECUTOR_PROVIDER.getExtension(resolveExecutorType(host));
        if (executor == null) {
            throw new IllegalStateException("未找到 Prometheus 远程查询 SPI: " + resolveExecutorType(host));
        }
        var result = executor.execute(host, buildRemoteHttpCommand(host, uri));
        if (result == null || !result.success()) {
            throw new IllegalStateException("Prometheus 远程查询失败: "
                    + abbreviate(result == null ? null : result.output(), 600));
        }
        return OBJECT_MAPPER.readTree(result.output());
    }

    /**
     * 针对不同操作系统构造远端 HTTP 拉取命令。
     */
    private static String buildRemoteHttpCommand(ServerHost host, String uri) {
        if (host != null && "WINDOWS".equalsIgnoreCase(host.getOsType())) {
            String escapedUri = uri.replace("'", "''");
            return "$response = Invoke-WebRequest -UseBasicParsing -Uri '" + escapedUri + "' -TimeoutSec 15; "
                    + "Write-Output $response.Content";
        }
        String escapedUri = uri.replace("'", "'\\''");
        return "(curl -fsSL --max-time 15 '" + escapedUri + "' || wget -qO- --timeout=15 '" + escapedUri + "')";
    }

    /**
     * 解析命令执行器类型，缺省回落到 local。
     */
    private static String resolveExecutorType(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getServerType())) {
            return "local";
        }
        return host.getServerType().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 截断远端错误输出，避免异常信息过长污染接口响应。
     */
    private static String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 校验 Prometheus API 返回状态，失败时抛出带查询文本的异常。
     */
    private static void validateSuccess(JsonNode root, String promql) {
        if (root == null || !"success".equalsIgnoreCase(root.path("status").asText())) {
            String message = root == null ? "empty-response" : root.path("error").asText(root.path("errorType").asText("unknown"));
            throw new IllegalStateException("Prometheus 查询失败: " + message + ", query=" + promql);
        }
    }

    /**
     * 合并磁盘序列的总量和剩余量。
     */
    private static void mergeDiskSeries(Map<String, DiskAccumulator> disks, JsonNode series, String field) {
        if (series == null || !series.isArray()) {
            return;
        }
        for (JsonNode item : series) {
            JsonNode metric = item.path("metric");
            String mountPoint = metric.path("mountpoint").asText(metric.path("device").asText());
            DiskAccumulator accumulator = disks.computeIfAbsent(mountPoint, key -> new DiskAccumulator());
            accumulator.name = metric.path("device").asText(null);
            accumulator.mountPoint = mountPoint;
            accumulator.fileSystem = metric.path("fstype").asText(null);
            double value = parsePrometheusValue(item.path("value").path(1)) == null
                    ? 0D
                    : parsePrometheusValue(item.path("value").path(1));
            if ("total".equals(field)) {
                accumulator.totalBytes = asLong(value);
            } else if ("free".equals(field)) {
                accumulator.freeBytes = asLong(value);
            }
        }
    }

    /**
     * 合并网卡累计字节和包计数。
     */
    private static void mergeNetworkSeries(Map<String, NetworkAccumulator> networks, JsonNode series, String field) {
        if (series == null || !series.isArray()) {
            return;
        }
        for (JsonNode item : series) {
            JsonNode metric = item.path("metric");
            String name = metric.path("device").asText(metric.path("interface").asText());
            NetworkAccumulator accumulator = networks.computeIfAbsent(name, key -> new NetworkAccumulator());
            accumulator.name = name;
            accumulator.displayName = name;
            Double value = parsePrometheusValue(item.path("value").path(1));
            if ("rxBytes".equals(field)) {
                accumulator.receivedBytes = asLong(value);
            } else if ("txBytes".equals(field)) {
                accumulator.transmittedBytes = asLong(value);
            } else if ("rxPackets".equals(field)) {
                accumulator.receivedPackets = asLong(value);
            } else if ("txPackets".equals(field)) {
                accumulator.transmittedPackets = asLong(value);
            } else if ("status".equals(field)) {
                accumulator.status = value != null && value > 0D ? "up" : "down";
            }
        }
    }

    /**
     * 合并 query_range 返回的时间序列点位。
     */
    private static void mergeHistorySeries(NavigableMap<Long, SnapshotBuilder> timeline, JsonNode matrix, String field) {
        if (matrix == null || !matrix.isArray()) {
            return;
        }
        for (JsonNode series : matrix) {
            JsonNode values = series.path("values");
            if (!values.isArray()) {
                continue;
            }
            for (JsonNode value : values) {
                long timestamp = Math.round(value.path(0).asDouble() * 1000D);
                Double metricValue = parsePrometheusValue(value.path(1));
                SnapshotBuilder builder = timeline.computeIfAbsent(timestamp, SnapshotBuilder::new);
                builder.apply(field, metricValue);
            }
        }
    }

    /**
     * 读取首个结果的 metric label。
     */
    private static String firstMetricLabel(JsonNode series, String... keys) {
        if (series == null || !series.isArray() || series.isEmpty()) {
            return null;
        }
        JsonNode metric = series.get(0).path("metric");
        for (String key : keys) {
            String value = metric.path(key).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 构造 Prometheus instance 的正则匹配串，未显式带端口时自动兼容 scrape 端口。
     */
    private static String instanceMatcher(ServerHost host) {
        String instance = ServerHostMetadataSupport.resolvePrometheusInstance(host);
        if (!StringUtils.hasText(instance)) {
            return ".*";
        }
        String escaped = escapePrometheusRegex(instance.trim());
        return instance.contains(":") ? escaped : escaped + "(:.+)?";
    }

    /**
     * 校验并读取 Prometheus 基础地址。
     */
    private static String requireBaseUrl(ServerHost host) {
        String baseUrl = ServerHostMetadataSupport.resolvePrometheusBaseUrl(host);
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("未配置 Prometheus 地址，请在 server metadataJson 中设置 prometheus.baseUrl");
        }
        return baseUrl;
    }

    /**
     * 把 Prometheus 返回的数字文本转成 Double。
     */
    private static Double parsePrometheusValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value) || "NaN".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 计算百分比并保留 2 位小数。
     */
    private static Double percent(long used, long total) {
        if (total <= 0L) {
            return null;
        }
        return scale2((used * 100D) / total);
    }

    /**
     * 把浮点值安全转换为 long。
     */
    private static long asLong(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0L;
        }
        return Math.max(Math.round(value), 0L);
    }

    /**
     * 统一 2 位小数展示，避免前端图表因浮点噪声抖动。
     */
    private static Double scale2(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return Math.round(value * 100D) / 100D;
    }

    /**
     * URL 编码查询串。
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 转义 Prometheus 正则中的特殊字符。
     */
    private static String escapePrometheusRegex(String value) {
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if ("\\.[]{}()+-*?^$|".indexOf(ch) >= 0) {
                builder.append('\\');
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * 用指定分隔符拼接非空片段。
     */
    private static String joinNonBlank(String delimiter, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    /**
     * Prometheus 磁盘累加器。
     */
    private static final class DiskAccumulator {

        private String name;
        private String mountPoint;
        private String fileSystem;
        private long totalBytes;
        private long freeBytes;

        private ServerDiskPartitionView toView() {
            long usedBytes = Math.max(totalBytes - freeBytes, 0L);
            return ServerDiskPartitionView.builder()
                    .name(name)
                    .mountPoint(mountPoint)
                    .fileSystem(fileSystem)
                    .label(null)
                    .totalBytes(totalBytes)
                    .usedBytes(usedBytes)
                    .freeBytes(freeBytes)
                    .usagePercent(percent(usedBytes, totalBytes))
                    .status("mounted")
                    .build();
        }
    }

    /**
     * Prometheus 网卡累加器。
     */
    private static final class NetworkAccumulator {

        private String name;
        private String displayName;
        private String status;
        private long receivedBytes;
        private long transmittedBytes;
        private long receivedPackets;
        private long transmittedPackets;

        private ServerNetworkInterfaceView toView() {
            return ServerNetworkInterfaceView.builder()
                    .name(name)
                    .displayName(displayName)
                    .status(StringUtils.hasText(status) ? status : "unknown")
                    .ipv4(null)
                    .macAddress(null)
                    .receivedBytes(receivedBytes)
                    .transmittedBytes(transmittedBytes)
                    .receivedPackets(receivedPackets)
                    .transmittedPackets(transmittedPackets)
                    .build();
        }
    }

    /**
     * 历史快照构造器。
     */
    private static final class SnapshotBuilder {

        private final long timestamp;
        private Double cpuUsage;
        private Double memoryUsed;
        private Double memoryTotal;
        private Double diskUsed;
        private Double diskTotal;
        private Double ioRead;
        private Double ioWrite;
        private Double rxPackets;
        private Double txPackets;

        private SnapshotBuilder(long timestamp) {
            this.timestamp = timestamp;
        }

        private void apply(String field, Double value) {
            switch (field) {
                case "cpuUsage" -> this.cpuUsage = value;
                case "memoryUsed" -> this.memoryUsed = value;
                case "memoryTotal" -> this.memoryTotal = value;
                case "diskUsed" -> this.diskUsed = value;
                case "diskTotal" -> this.diskTotal = value;
                case "ioRead" -> this.ioRead = value;
                case "ioWrite" -> this.ioWrite = value;
                case "rxPackets" -> this.rxPackets = value;
                case "txPackets" -> this.txPackets = value;
                default -> {
                }
            }
        }

        private ServerMetricsSnapshot toSnapshot(ServerHost host, Integer cpuCores) {
            long memoryTotalBytes = asLong(memoryTotal);
            long memoryUsedBytes = asLong(memoryUsed);
            long diskTotalBytes = asLong(diskTotal);
            long diskUsedBytes = asLong(diskUsed);
            return ServerMetricsSnapshot.builder()
                    .serverId(host == null ? null : host.getServerId())
                    .serverCode(host == null ? null : host.getServerCode())
                    .status("ONLINE")
                    .online(Boolean.TRUE)
                    .latencyMs(null)
                    .cpuUsage(scale2(cpuUsage))
                    .cpuCores(cpuCores)
                    .memoryUsage(percent(memoryUsedBytes, memoryTotalBytes))
                    .memoryTotalBytes(memoryTotalBytes)
                    .memoryUsedBytes(memoryUsedBytes)
                    .diskUsage(percent(diskUsedBytes, diskTotalBytes))
                    .diskTotalBytes(diskTotalBytes)
                    .diskUsedBytes(diskUsedBytes)
                    .ioReadBytesPerSecond(scale2(ioRead))
                    .ioWriteBytesPerSecond(scale2(ioWrite))
                    .networkRxPacketsPerSecond(scale2(rxPackets))
                    .networkTxPacketsPerSecond(scale2(txPackets))
                    .collectTimestamp(timestamp)
                    .detailMessage("Prometheus 历史回放")
                    .build();
        }
    }
}
