package com.chua.starter.monitor.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 按 URL 维度的访问趋势。
 *
 * <p>与 {@link NginxTrendVO} 是两条独立链路：那个来自采集器落库的指标表、固定 24 小时窗口；
 * 这个直接解析 access log 尾部，因此<strong>时间跨度由日志密度决定</strong>——
 * 高流量站最近 20000 行可能只覆盖几分钟，低流量站可能覆盖几小时。
 * {@code fromTime} / {@code toTime} 给出实际覆盖区间，前端必须把它标出来，
 * 不能让人把「最近 N 行」误读成「最近 N 分钟」。</p>
 *
 * @param logName       扫描的日志文件名
 * @param totalLines    成功解析的行数
 * @param scannedLines  实际扫描的总行数
 * @param fromEpoch     实际覆盖起点（epoch 秒，前端画图与标注的权威来源）
 * @param toEpoch       实际覆盖终点（epoch 秒）
 * @param fromTime      实际覆盖起点（服务器本地时间，仅供人读与排障）
 * @param toTime        实际覆盖终点（服务器本地时间）
 * @param bucketSeconds 每个时间桶的宽度（秒）
 * @param message       口径说明
 * @param series        各 URL 的时间序列，按命中数从多到少
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "按 URL 的访问趋势")
public record NginxUrlTrendVO(
        @Schema(description = "日志文件名", example = "access.log") String logName,
        @Schema(description = "成功解析行数", example = "2000") int totalLines,
        @Schema(description = "扫描行数", example = "2000") int scannedLines,
        @Schema(description = "实际覆盖起点（epoch 秒）", example = "1790595012") long fromEpoch,
        @Schema(description = "实际覆盖终点（epoch 秒）", example = "1790596564") long toEpoch,
        @Schema(description = "实际覆盖起点（服务器本地时间文本）", example = "2026-09-28T07:40:12") String fromTime,
        @Schema(description = "实际覆盖终点（服务器本地时间文本）", example = "2026-09-28T07:56:04") String toTime,
        @Schema(description = "时间桶宽度（秒）", example = "60") int bucketSeconds,
        @Schema(description = "口径说明") String message,
        @Schema(description = "各 URL 的时间序列") List<UrlSeries> series
) {

    /**
     * 单个 URL 的时间序列。
     *
     * @param path    归一化后的路径（数字 ID 与 UUID 已替换成占位符）
     * @param hits    覆盖区间内的总命中数
     * @param buckets 按时间桶计数的序列，长度与桶数一致
     */
    public record UrlSeries(
            @Schema(description = "路径", example = "/api/orders/:id") String path,
            @Schema(description = "总命中数", example = "128") long hits,
            @Schema(description = "各桶计数", example = "[3, 5, 2]") List<Long> buckets
    ) {
    }
}
