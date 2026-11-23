package com.chua.starter.datapool.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源连通性指标采样点（一行 = 一次探测）。
 *
 * <p>表 {@code datasource_metric}：业务列以完整表名作前缀；采样时刻即行创建时间，
 * 走 {@link SysBase} 的 {@code create_time} 通用审计列。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源连通性指标采样点")
@TableName("datasource_metric")
public class MsDatasourceMetric extends SysBase {

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
    @TableField("datasource_metric_datasource_id")
    @Schema(description = "数据源 ID", example = "20")
    private Long datasourceId;

    /**
     * 数据类型（冗余）
     */
    @TableField("datasource_metric_datasource_type")
    @Schema(description = "数据类型", example = "jdbc")
    private String datasourceType;

    /**
     * 1 连通 / 0 不通 / null 未判定
     */
    @TableField("datasource_metric_success")
    @Schema(description = "1 连通 0 不通 null 未判定", example = "1")
    private Integer success;

    /**
     * 建连耗时（毫秒）
     */
    @TableField("datasource_metric_connect_latency")
    @Schema(description = "建连耗时（毫秒）", example = "18")
    private Long connectLatency;

    /**
     * 会话耗时（毫秒）
     */
    @TableField("datasource_metric_session_latency")
    @Schema(description = "会话耗时（毫秒）", example = "3")
    private Long sessionLatency;

    /**
     * 采样时刻客户端连接数（瞬时值）。
     *
     * <p>目前仅 jdbc(MySQL/MariaDB/PostgreSQL) 与 redis 能取到，其余类型为 null ——
     * null 在图上是缺格，比编一个 0 诚实。</p>
     */
    @TableField("datasource_metric_connection_count")
    @Schema(description = "采样时刻客户端连接数，取不到为 null", example = "12")
    private Long connectionCount;

    /**
     * 累计慢查询数（MySQL/MariaDB 服务器状态，取不到为 null）。
     */
    @TableField("datasource_metric_slow_queries")
    @Schema(description = "累计慢查询数", example = "13")
    private Long slowQueries;

    /**
     * 正在执行的线程数（MySQL/MariaDB 负载近似指标，取不到为 null）。
     */
    @TableField("datasource_metric_threads_running")
    @Schema(description = "正在执行的线程数", example = "4")
    private Long threadsRunning;

    /**
     * 累计提问次数（MySQL/MariaDB 自身计数，取不到为 null）。
     */
    @TableField("datasource_metric_questions")
    @Schema(description = "累计提问次数", example = "88123")
    private Long questions;

    /**
     * 服务器已运行秒数（MySQL/MariaDB，取不到为 null）。
     */
    @TableField("datasource_metric_uptime")
    @Schema(description = "服务器已运行秒数", example = "86400")
    private Long uptime;

    /**
     * 历史峰值客户端连接数（MySQL/MariaDB，取不到为 null）。
     */
    @TableField("datasource_metric_max_used_connections")
    @Schema(description = "历史峰值客户端连接数", example = "30")
    private Long maxUsedConnections;

    /**
     * 实例内存占用（字节，MySQL/MariaDB，取不到为 null）。
     *
     * <p>来自 {@code performance_schema.memory_summary_global_by_event_name} 的
     * InnoDB 事件当前占用合计 —— 是 MySQL 进程内部内存（缓冲池 / 数据字典等），
     * 不是宿主机内存；前端卡片如实标注口径。</p>
     */
    @TableField("datasource_metric_instance_memory")
    @Schema(description = "实例内存占用（字节），取不到为 null", example = "536870912")
    private Long memoryBytes;

    /**
     * 1 经 SSH 隧道 / 0 直连 / null 未判定
     */
    @TableField("datasource_metric_tunneled")
    @Schema(description = "1 经隧道 0 直连", example = "0")
    private Integer tunneled;

    /**
     * 失败原因
     */
    @TableField("datasource_metric_error_message")
    @Schema(description = "失败原因")
    private String errorMessage;
}
