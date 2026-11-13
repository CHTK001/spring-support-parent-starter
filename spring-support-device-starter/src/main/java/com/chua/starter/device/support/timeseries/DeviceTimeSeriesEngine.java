package com.chua.starter.device.support.timeseries;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备时序引擎统一契约。
 *
 * <p>传感器记录、门禁记录、车闸记录的「按时间存 / 按时间查」都走这一个接口，
 * 具体落在哪种存储由 {@code plugin.device.timeseries.engine} 决定：</p>
 * <ul>
 *   <li>{@code bean}（默认）：MySQL 明细表 {@code device_series_point}，随宿主数据源走；</li>
 *   <li>{@code influxdb}：InfluxDB 1.x HTTP 接口（measurement + tag + field）；</li>
 *   <li>{@code redis}：Redis ZSET（score=毫秒时间戳，member=时序点 JSON）。</li>
 * </ul>
 *
 * <p>契约只包含四件事：写入、查询、计数、按保留期清理。降采样、曲线聚合由调用方
 * 按 {@link #query(DeviceSeriesQuery)} 的结果自行处理 —— 各后端聚合语法不同，
 * 在这里做统一聚合只会让「换引擎后曲线形状变了」。</p>
 *
 * <p><b>错误语义</b>：引擎不可用时，{@link #query} 返回空列表、{@link #count} 返回 0、
 * {@link #write} 抛 {@link IllegalStateException}（写入丢数据必须立刻可见）；
 * 参数非法抛 {@link IllegalArgumentException}。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
public interface DeviceTimeSeriesEngine {

    /**
     * 引擎标识，与配置值一致。
     *
     * @return {@code bean} / {@code influxdb} / {@code redis}
     */
    String engine();

    /**
     * 引擎当前是否可用（配置齐全、宿主依赖存在、未关闭）。
     *
     * @return 可用返回 {@code true}
     */
    boolean isAvailable();

    /**
     * 不可用原因；可用时返回空串。
     *
     * <p>页面上要如实告诉用户「为什么切了 influxdb 却没有数据」，
     * 而不是把空列表当成「没有记录」。</p>
     *
     * @return 原因文本
     */
    String unavailableReason();

    /**
     * 写入一个时序点。
     *
     * @param point 时序点（category / deviceId / key / time 必填）
     * @throws IllegalArgumentException 必填字段缺失
     * @throws IllegalStateException    引擎不可用或后端写入失败
     */
    void write(DeviceSeriesPoint point);

    /**
     * 批量写入。
     *
     * <p>空列表直接返回 0；单点失败按后端能力决定是整批失败还是跳过，
     * 实现必须在日志里留下痕迹，不得静默吞掉。</p>
     *
     * @param points 时序点列表
     * @return 实际写入条数
     */
    int writeBatch(List<DeviceSeriesPoint> points);

    /**
     * 按时序条件查询。
     *
     * @param query 查询条件
     * @return 时序点列表，按 {@link DeviceSeriesQuery#isAscending()} 排序；无数据返回空列表
     */
    List<DeviceSeriesPoint> query(DeviceSeriesQuery query);

    /**
     * 按条件计数（不拉明细）。
     *
     * @param query 查询条件
     * @return 条数
     */
    long count(DeviceSeriesQuery query);

    /**
     * 按保留期清理某分类的历史点。
     *
     * @param category 分类编码，{@code null} 表示全部分类
     * @param before   删除该时间之前的点
     * @return 删除条数；后端无法精确统计时返回 -1（代表「已执行清理」）
     */
    int purge(String category, LocalDateTime before);
}
