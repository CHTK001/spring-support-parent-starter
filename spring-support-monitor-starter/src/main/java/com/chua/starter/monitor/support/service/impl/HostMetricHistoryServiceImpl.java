package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.common.support.lang.datasource.series.impl.DataSourceSeriesEngine;
import com.chua.starter.monitor.support.service.HostMetricHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 主机内置指标历史服务实现。
 *
 * <p><b>写入、查询、保留期清理全部走时序抽象 {@link SeriesEngine}</b>：
 * <ul>
 *   <li>写入：按主机宽表批量落一行；</li>
 *   <li>查询：按五个逻辑 target 取单指标曲线后按时间戳对齐合并；</li>
 *   <li>清理：委托 {@link SeriesEngine#purge}。</li>
 * </ul>
 * 当前 JDBC 实现为 {@link DataSourceSeriesEngine}，后续切换 InfluxDB / Redis 只需改
 * {@code plugin.monitor.timeseries.engine}，本类无感知。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class HostMetricHistoryServiceImpl implements HostMetricHistoryService {

    /**
     * 主机指标宽表物理名（writeBatch / purge 的 target）
     */
    private static final String HOST_TABLE = "ms_host_metric_history";

    /**
     * 允许的最小回溯小时数
     */
    private static final int MIN_LOOKBACK_HOURS = 1;

    /**
     * 允许的最大回溯小时数（7 天，与 {@link HostMetricHistoryService#list(Long, int)} 声明一致）
     */
    private static final int MAX_LOOKBACK_HOURS = 24 * 7;

    /**
     * 五个数值列后缀与输出行下标顺序：CPU / 内存 / 磁盘 / 磁盘读 / 磁盘写
     *
     * <p>这些是**指标名（数值列后缀）**，不是引擎的 target。引擎把 target 当表名，
     * 拼成 {@code SELECT {target}_{metricName} FROM {target}}，所以读曲线必须传
     * {@code series(HOST_TABLE, hostId, 后缀, hours, window)} —— 与 {@link #record}
     * 写入的是同一张宽表 {@link #HOST_TABLE}。</p>
     *
     * <p>⚠ 曾把后缀直接当 target 传进 4 参重载（其 metricName 恒为 null），
     * 于是每次查询都变成 {@code SELECT ... FROM cpu_usage}（库中并无此表）：
     * 异常被逐指标 {@code catch} 吞掉，接口永远返回空数组且 code=00000，
     * 而库里其实一直在正常写入（实测 18 万行 / 4 台主机 / 秒级在写）。</p>
     */
    private static final List<String> CURVE_TARGETS = List.of(
            "cpu_usage",
            "mem_usage",
            "disk_usage",
            "disk_read_kbs",
            "disk_write_kbs");

    /**
     * 时序引擎（读写与清理唯一入口）
     */
    @Autowired
    private SeriesEngine seriesEngine;

    /**
     * 历史保留天数
     */
    @Value("${monitor.history.retention-days:7}")
    private int retentionDays;

    @Override
    public void record(Long hostId, Double cpu, Double mem, Double disk,
                       Double diskReadKbs, Double diskWriteKbs) {
        if (hostId == null || !seriesEngine.isAvailable()) {
            return;
        }
        // key 即宽表物理列后缀，顺序固定便于排查
        Map<String, Double> metrics = new LinkedHashMap<>();
        putIfNotNull(metrics, "cpu_usage", cpu);
        putIfNotNull(metrics, "mem_usage", mem);
        putIfNotNull(metrics, "disk_usage", disk);
        putIfNotNull(metrics, "disk_read_kbs", diskReadKbs);
        putIfNotNull(metrics, "disk_write_kbs", diskWriteKbs);
        if (metrics.isEmpty()) {
            return;
        }
        try {
            seriesEngine.writeBatch(HOST_TABLE, hostId, metrics, System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.debug("[HostHistory] 写入主机指标历史失败 host={}: {}", hostId, e.getMessage());
        }
    }

    @Override
    public List<List<Object>> list(Long hostId, int hours) {
        if (hostId == null || !seriesEngine.isAvailable()) {
            return List.of();
        }
        // 接口声明的回溯范围是 1..168 小时。原来用 Math.max(1, Math.min(hours, 24*7))
        // 夹取：传 0 被悄悄当成 1 小时、传 720 被悄悄砍成 7 天，调用方拿到的曲线
        // 与自己的请求不符却毫无察觉。范围既已写进契约，就显式拒绝越界而不是改写入参。
        if (hours < MIN_LOOKBACK_HOURS || hours > MAX_LOOKBACK_HOURS) {
            throw new IllegalArgumentException("回溯小时数必须在 " + MIN_LOOKBACK_HOURS
                    + ".." + MAX_LOOKBACK_HOURS + " 之间: " + hours);
        }
        String window = windowForHours(hours);
        // ts → [cpu, mem, disk, read, write]，按时间升序
        TreeMap<Long, Double[]> merged = new TreeMap<>();
        for (int i = 0; i < CURVE_TARGETS.size(); i++) {
            List<List<Object>> curve;
            try {
                // target 必须是宽表物理名，指标名单独走 metricName：写侧用的是
                // writeBatch(HOST_TABLE, ...)，读侧必须问同一张表，否则查的是
                // 「按指标分表」世界里不存在的 cpu_usage / mem_usage …
                curve = seriesEngine.series(HOST_TABLE, hostId, CURVE_TARGETS.get(i), hours, window);
            } catch (RuntimeException e) {
                log.debug("[HostHistory] 查询曲线失败 host={} target={}: {}",
                        hostId, CURVE_TARGETS.get(i), e.getMessage());
                continue;
            }
            if (curve == null) {
                continue;
            }
            for (List<Object> pair : curve) {
                if (pair.size() < 2 || !(pair.get(0) instanceof Number) || !(pair.get(1) instanceof Number)) {
                    continue;
                }
                long ts = ((Number) pair.get(0)).longValue();
                double value = ((Number) pair.get(1)).doubleValue();
                merged.computeIfAbsent(ts, k -> new Double[CURVE_TARGETS.size()])[i] = value;
            }
        }
        List<List<Object>> rows = new ArrayList<>(merged.size());
        for (Map.Entry<Long, Double[]> entry : merged.entrySet()) {
            List<Object> row = new ArrayList<>(1 + CURVE_TARGETS.size());
            row.add(entry.getKey());
            for (Double value : entry.getValue()) {
                row.add(value);
            }
            rows.add(row);
        }
        return rows;
    }

    @Override
    public int cleanup(int days) {
        if (!seriesEngine.isAvailable()) {
            return 0;
        }
        long cutoff = System.currentTimeMillis()
                - (long) Math.max(1, days) * 24L * 3600_000L;
        try {
            return seriesEngine.purge(HOST_TABLE, null, cutoff);
        } catch (RuntimeException e) {
            // 清理失败不能降级成 0：0 在契约里表示「后端不支持清理」或「本批无过期点」，
            // 把它当成正常返回值会让保留期策略静默失效、时序表无限增长。
            // 定时任务里抛出即可交由 Spring 的错误处理器记录，不再吞成 debug 日志。
            log.error("[HostHistory] 保留期清理失败 target={} 保留天数={}", HOST_TABLE, retentionDays, e);
            throw e;
        }
    }

    /**
     * 每天凌晨按配置保留天数执行清理。
     */
    @Scheduled(cron = "0 17 3 * * ?")
    public void scheduledCleanup() {
        int removed = cleanup(retentionDays);
        if (removed > 0) {
            log.info("[HostHistory] 保留期清理完成，删除 {} 行", removed);
        }
    }

    /**
     * 按回溯时长选择聚合窗口，兼顾曲线分辨率与点数上限。
     *
     * @param hours 回溯小时数
     * @return 窗口字符串
     */
    private String windowForHours(int hours) {
        if (hours <= 1) {
            return "30s";
        }
        if (hours <= 6) {
            return "2m";
        }
        if (hours <= 24) {
            return "5m";
        }
        return "15m";
    }

    /**
     * 非空才放入指标映射。
     *
     * @param metrics 指标容器
     * @param key     列后缀
     * @param value   值
     */
    private void putIfNotNull(Map<String, Double> metrics, String key, Double value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }
}
