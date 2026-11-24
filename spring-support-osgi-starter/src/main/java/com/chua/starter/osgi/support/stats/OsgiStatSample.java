package com.chua.starter.osgi.support.stats;

import java.util.Map;

/**
 * 框架体征采样点（{@code GET /osgi/stats/history} 的元素）。
 *
 * <p>由 {@link OsgiStatsCollector} 周期采样 {@code OsgiLauncher#getFrameworkStats()}
 * 产出，按时间升序返回。前端趋势图据此绘制 bundle 总数 / 活跃数曲线；
 * {@code bundleStates} 保留完整状态分布，便于后续按状态拆分展示。</p>
 *
 * <p>只采样、不落库：宿主重启后历史清零，趋势从新积累。</p>
 *
 * @param timestamp         采样时间戳（毫秒）
 * @param frameworkActive   框架是否处于活跃状态
 * @param totalBundles      bundle 总数（含系统 bundle）
 * @param activeBundles     处于 ACTIVE 状态的 bundle 数
 * @param serviceTypes      服务注册表已登记的服务类型数
 * @param autoStartFailures 自动启动失败累计数
 * @param bundleStates      各生命周期状态 → bundle 数
 * @author CH
 * @since 4.0.0.52
 */
public record OsgiStatSample(long timestamp,
                             boolean frameworkActive,
                             int totalBundles,
                             int activeBundles,
                             int serviceTypes,
                             int autoStartFailures,
                             Map<String, Long> bundleStates) {

    /**
     * 紧凑构造器：状态分布做防御性拷贝，采样点不可变。
     *
     * @param bundleStates 各生命周期状态 → bundle 数，可为 {@code null}
     */
    public OsgiStatSample {
        bundleStates = bundleStates == null ? Map.of() : Map.copyOf(bundleStates);
    }
}
