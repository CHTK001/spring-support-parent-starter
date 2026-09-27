package com.chua.starter.monitor.support.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Nginx 指标趋势查询结果。
 *
 * <p><strong>为什么必须换算速率</strong>：{@code stub_status} 的
 * {@code accepts / handled / requests} 是「worker 启动以来的累计计数」，
 * 单调递增。直接把它当曲线画出来只会得到一条斜向上的直线，看不出任何波动。
 * 所以本 VO 同时给出原始值与按相邻两点换算出的速率（每秒增量），
 * 前端画趋势用 {@code rate}，要核对累计量用 {@code value}。</p>
 *
 * <p><strong>worker 重启怎么处理</strong>：nginx 重启后计数器归零，
 * 差值会变成负数。此时该点 {@code rate} 置为 null（断点）而不是画一个假的负尖峰，
 * 避免把「重启」误读成「流量暴跌」。</p>
 *
 * @param datasourceId 数据源 id
 * @param from         起始时刻
 * @param to           结束时刻
 * @param interval     实际使用的采样间隔（分钟）
 * @param points       曲线点
 * @param message      面向用户的说明（无数据时说明原因）
 * @author CH
 * @since 4.0.0
 */
public record NginxTrendVO(
        Long datasourceId,
        LocalDateTime from,
        LocalDateTime to,
        int interval,
        List<Point> points,
        String message
) {

    /**
     * 规范化集合组件，阻断外部别名写入。
     *
     * <p>{@code points} 允许为 {@code null}（调用方显式判空后按空处理，
     * 见 AgentEndpointController），因此保留 null 语义，
     * 不使用 {@code requireNonNull}。</p>
     */
    public NginxTrendVO {
        points = points == null ? null : List.copyOf(points);
    }

    /**
     * 单个曲线点。
     *
     * @param time  采样时刻
     * @param name  指标名
     * @param value 原始值（累计类为累计计数）
     * @param rate  每秒增量；瞬时类等于 value，累计类为差值/秒；不可算时为 null
     */
    public record Point(
            LocalDateTime time,
            String name,
            Double value,
            Double rate
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
    public static NginxTrendVO empty(Long datasourceId, LocalDateTime from, LocalDateTime to, int interval, String message) {
        return new NginxTrendVO(datasourceId, from, to, interval, List.of(), message);
    }
}
