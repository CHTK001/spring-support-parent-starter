package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prometheus 拉取采集 Provider（PROMETHEUS）
 * <p>
 * 从目标主机 {@code http://{address}:{port}/metrics} 抓取 Prometheus 文本格式指标，
 * 解析 CPU / 内存 / 磁盘使用率并回写。适合部署了 node_exporter 等组件的主机。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusHostCollectProvider implements HostCollectProvider {

    /** Prometheus 指标端点路径 */
    private static final String METRICS_PATH = "/metrics";

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    /** CPU 空闲秒数指标 */
    private static final Pattern CPU_IDLE = Pattern.compile(
            "^node_cpu_seconds_total\\{.*?cpu=\"[0-9]+\",.*?mode=\"idle\".*?\\}\\s+([0-9.eE+-]+)",
            Pattern.MULTILINE);

    /** 全部 CPU 秒数指标 */
    private static final Pattern CPU_TOTAL = Pattern.compile(
            "^node_cpu_seconds_total\\{.*?mode=\"[a-z]+\".*?\\}\\s+([0-9.eE+-]+)",
            Pattern.MULTILINE);

    /** 内存总量(字节) */
    private static final Pattern MEM_TOTAL = Pattern.compile(
            "^node_memory_MemTotal_bytes\\s+([0-9.eE+-]+)", Pattern.MULTILINE);

    /** 内存可用量(字节) */
    private static final Pattern MEM_AVAILABLE = Pattern.compile(
            "^node_memory_MemAvailable_bytes\\s+([0-9.eE+-]+)", Pattern.MULTILINE);

    /** 磁盘总量(字节) */
    private static final Pattern DISK_TOTAL = Pattern.compile(
            "^node_filesystem_size_bytes\\{.*?mountpoint=\"/\".*?\\}\\s+([0-9.eE+-]+)",
            Pattern.MULTILINE);

    /** 磁盘可用量(字节) */
    private static final Pattern DISK_AVAILABLE = Pattern.compile(
            "^node_filesystem_avail_bytes\\{.*?mountpoint=\"/\".*?\\}\\s+([0-9.eE+-]+)",
            Pattern.MULTILINE);

    @Override
    public HostCollectType type() {
        return HostCollectType.PROMETHEUS;
    }

    @Override
    public CollectOutcome collect(HostCollectContext context) {
        MsServerHost host = context == null ? null : context.getHost();
        if (host == null || host.getId() == null) {
            return CollectOutcome.fail("主机数据不完整");
        }
        String address = host.getServerHostAddress();
        if (address == null || address.isBlank()) {
            return CollectOutcome.fail("主机地址为空");
        }
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0 ? 9100 : host.getServerHostPort();
        String url = "http://" + address.trim() + ":" + port + METRICS_PATH;
        try {
            String body = fetchBody(url);
            applyMetrics(host, body);
            hostMapper.updateById(host);
            return CollectOutcome.ok("Prometheus 采集成功");
        } catch (Exception e) {
            log.warn("[collect] PROMETHEUS 采集失败 hostId={} url={}: {}", host.getId(), url, e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }

    /**
     * 拉取指标文本
     *
     * @param url 端点
     * @return 指标文本
     */
    private String fetchBody(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IllegalStateException("metrics 端点返回 " + code);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 从 Prometheus 文本解析并回写主机
     *
     * @param host 主机
     * @param body 指标文本
     */
    private void applyMetrics(MsServerHost host, String body) {
        // CPU 使用率 = 1 - idle/total
        double idleSum = sumGroup(CPU_IDLE, body);
        double totalSum = sumGroup(CPU_TOTAL, body);
        if (totalSum > 0 && idleSum >= 0) {
            double usage = (1 - idleSum / totalSum) * 100;
            // 回写为 CPU 使用率（0-100 取整），核数无法从 Prometheus 直接得出，保留原有值
            if (host.getServerHostCpuCores() == null) {
                host.setServerHostCpuCores((int) Math.round(usage));
            }
        }
        // 内存
        double memTotal = firstDouble(MEM_TOTAL, body);
        double memAvailable = firstDouble(MEM_AVAILABLE, body);
        if (memTotal > 0) {
            long usedMb = Math.round((memTotal - Math.max(memAvailable, 0)) / 1024 / 1024);
            host.setServerHostMemoryMb(usedMb);
        }
        // 磁盘
        double diskTotal = firstDouble(DISK_TOTAL, body);
        double diskAvailable = firstDouble(DISK_AVAILABLE, body);
        if (diskTotal > 0) {
            long totalGb = Math.round(diskTotal / 1024 / 1024 / 1024);
            long availGb = Math.round(Math.max(diskAvailable, 0) / 1024 / 1024 / 1024);
            host.setServerHostDiskGb(totalGb);
            if (totalGb - availGb > 0 && host.getServerHostDescription() == null) {
                host.setServerHostDescription("磁盘已用 " + (totalGb - availGb) + " GB");
            }
        }
    }

    /**
     * 求和所有匹配组
     *
     * @param pattern 正则
     * @param body    文本
     * @return 求和结果，无匹配返回 -1
     */
    private double sumGroup(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        double sum = 0;
        boolean found = false;
        while (m.find()) {
            try {
                sum += Double.parseDouble(m.group(1));
                found = true;
            } catch (NumberFormatException ignored) {
            }
        }
        return found ? sum : -1;
    }

    /**
     * 取首个匹配值
     *
     * @param pattern 正则
     * @param body    文本
     * @return 值，无匹配返回 -1
     */
    private double firstDouble(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
}
