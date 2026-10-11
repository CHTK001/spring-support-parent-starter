package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源连通性指标采样点（一行 = 一次探测）。
 *
 * @author CH
 * @since 4.0.0.43
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "数据源连通性指标采样点")
@TableName("ms_datasource_metric")
public class MsDatasourceMetric implements Serializable {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 数据源 ID
     */
    @TableField("datasource_id")
    @Schema(description = "数据源 ID", example = "20")
    private Long datasourceId;

    /**
     * 数据类型（冗余）
     */
    @TableField("datasource_type")
    @Schema(description = "数据类型", example = "jdbc")
    private String datasourceType;

    /**
     * 1 连通 / 0 不通 / null 未判定
     */
    @TableField("success")
    @Schema(description = "1 连通 0 不通 null 未判定", example = "1")
    private Integer success;

    /**
     * 建连耗时（毫秒）
     */
    @TableField("connect_latency")
    @Schema(description = "建连耗时（毫秒）", example = "18")
    private Long connectLatency;

    /**
     * 会话耗时（毫秒）
     */
    @TableField("session_latency")
    @Schema(description = "会话耗时（毫秒）", example = "3")
    private Long sessionLatency;

    /**
     * 采样时刻客户端连接数（瞬时值）。
     *
     * <p>目前仅 jdbc(MySQL/MariaDB/PostgreSQL) 与 redis 能取到，其余类型为 null ——
     * null 在图上是缺格，比编一个 0 诚实。</p>
     */
    @TableField("connection_count")
    @Schema(description = "采样时刻客户端连接数，取不到为 null", example = "12")
    private Long connectionCount;

    /**
     * 1 经 SSH 隧道 / 0 直连 / null 未判定
     */
    @TableField("tunneled")
    @Schema(description = "1 经隧道 0 直连", example = "0")
    private Integer tunneled;

    /**
     * 失败原因
     */
    @TableField("error_message")
    @Schema(description = "失败原因")
    private String errorMessage;

    /**
     * 采样时刻
     */
    @TableField("create_time")
    @Schema(description = "采样时刻", example = "2026-10-11T09:00:00")
    private LocalDateTime createTime;
}