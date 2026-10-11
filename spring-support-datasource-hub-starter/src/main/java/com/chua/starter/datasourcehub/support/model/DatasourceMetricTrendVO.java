package com.chua.starter.datasourcehub.support.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源连通性指标趋势。
 *
 * <p>与 {@code NginxTrendVO} 的差别：nginx 的曲线画的是「速率」，因为它的原始
 * 指标是单调递增的累计计数，直接画没有意义。这里画的每一项都是<b>瞬时值</b>
 * （这次探测通没通、这次连了多久），本身就能画，不需要再换算成增量。</p>
 *
 * @param datasourceId 数据源 id
 * @param from         起始时刻
 * @param to           结束时刻
 * @param interval     采样间隔（秒）
 * @param points       采样点
 * @param message      面向用户的说明（无数据时说明原因）
 * @author CH
 * @since 4.0.0.43
 */
public record DatasourceMetricTrendVO(
        Long datasourceId,
        LocalDateTime from,
        LocalDateTime to,
        int interval,
        List<Point> points,
        String message
) {

    /**
     * 规范化集合，阻断外部别名写入。
     *
     * @param datasourceId 数据源 id
     * @param from         起始时刻
     * @param to           结束时刻
     * @param interval     采样间隔
     * @param points       采样点
     * @param message      说明
     */
    public DatasourceMetricTrendVO {
        points = points == null ? null : List.copyOf(points);
    }

    /**
     * 单个采样点。
     *
     * <p>{@code success} 用 {@link Boolean} 而不是 {@code int}：连通与否不是数值，
     * 用 1/0 表达会让 echarts 把它当连续量去连线，中间的失败点被画成「0.5 通」这种
     * 不存在的状态。失败点传 {@code null} 由 echarts 断开，比 0 更诚实。</p>
     *
     * @param time             采样时刻
     * @param success          连通与否，未判定为 null
     * @param connectLatencyMs 建连耗时（毫秒）
     * @param sessionLatencyMs 会话耗时（毫秒）
     * @param tunneled         是否经隧道
     * @param errorMessage     失败原因
     * @param connectionCount  采样时刻客户端连接数；类型不支持时为 null（图上缺格）
     */
    public record Point(
            LocalDateTime time,
            Boolean success,
            Long connectLatencyMs,
            Long sessionLatencyMs,
            Boolean tunneled,
            String errorMessage,
            Long connectionCount
    ) {
    }

    /**
     * 构造「无数据」结果。
     *
     * @param datasourceId 数据源 id
     * @param from         起始时刻
     * @param to           结束时刻
     * @param interval     采样间隔
     * @param message      说明
     * @return 趋势对象
     */
    public static DatasourceMetricTrendVO empty(Long datasourceId, LocalDateTime from, LocalDateTime to,
                                                int interval, String message) {
        return new DatasourceMetricTrendVO(datasourceId, from, to, interval, List.of(), message);
    }
}