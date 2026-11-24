package com.chua.starter.osgi.support.stats;

/**
 * 插件操作统计（{@code GET /osgi/stats/actions} 的元素）。
 *
 * <p>由 {@link OsgiStatsCollector} 监听插件操作日志事件（success / failure 终态）
 * 按操作类型聚合产出。只统计终态：start 阶段事件不计数，避免同一次操作被重复统计。</p>
 *
 * @param action             操作类型：install / update / start / stop / uninstall / delete-jar 等
 * @param total              总次数（成功 + 失败）
 * @param success            成功次数
 * @param failure            失败次数
 * @param avgDurationMillis  平均耗时（毫秒）；按全部终态事件平均，start 阶段不计入
 * @param lastTimestamp      最近一次该操作的时间戳（毫秒）
 * @author CH
 * @since 4.0.0.52
 */
public record OsgiActionStat(String action,
                             long total,
                             long success,
                             long failure,
                             long avgDurationMillis,
                             long lastTimestamp) {
}
