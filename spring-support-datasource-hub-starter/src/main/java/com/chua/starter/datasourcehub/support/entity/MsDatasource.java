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
 * 数据源实体（数据源中心台账）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "数据源")
@TableName("ms_datasource")
public class MsDatasource implements Serializable {

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
     * 数据源名称
     */
    @TableField("datasource_name")
    @Schema(description = "数据源名称", example = "订单库")
    private String datasourceName;

    /**
     * 数据源类型（jdbc/redis/influxdb/neo4j/nacos/zookeeper/nginx）
     */
    @TableField("datasource_type")
    @Schema(description = "数据源类型", example = "jdbc")
    private String datasourceType;

    /**
     * 主机地址
     */
    @TableField("datasource_host")
    @Schema(description = "主机地址", example = "127.0.0.1")
    private String datasourceHost;

    /**
     * 端口，空则按类型默认
     */
    @TableField("datasource_port")
    @Schema(description = "端口", example = "3306")
    private Integer datasourcePort;

    /**
     * 库名/命名空间；jdbc 可存完整 JDBC URL
     */
    @TableField("datasource_database")
    @Schema(description = "库名", example = "demo")
    private String datasourceDatabase;

    /**
     * 用户名
     */
    @TableField("datasource_username")
    @Schema(description = "用户名", example = "root")
    private String datasourceUsername;

    /**
     * 口令
     */
    @TableField("datasource_password")
    @Schema(description = "口令", example = "******")
    private String datasourcePassword;

    /**
     * 备注
     */
    @TableField("datasource_remark")
    @Schema(description = "备注", example = "只读账号")
    private String datasourceRemark;

    /**
     * 状态，0 停用、1 启用。
     *
     * <p>停用的数据源不允许被采集流程、SQL 节点引用，避免流程跑到一半才发现目标库不可用。</p>
     */
    @TableField("datasource_status")
    @Schema(description = "状态 0停用 1启用", example = "1")
    private Integer datasourceStatus;

    /**
     * JDBC 驱动类名。
     *
     * <p>非 MySQL 系的库建议显式指定；为空时由连接层按 URL 推断。</p>
     */
    @TableField("datasource_driver_class_name")
    @Schema(description = "JDBC 驱动类名", example = "org.postgresql.Driver")
    private String datasourceDriverClassName;

    /**
     * 连接延迟（毫秒）。
     *
     * <p>建立连接耗时：TCP 建链 + 协议握手。由「测试连接」回写，覆盖写、不留历史。
     * 为空表示从未测试过或测试未通过。</p>
     */
    @TableField("datasource_connect_latency_ms")
    @Schema(description = "连接延迟(毫秒)", example = "12")
    private Long datasourceConnectLatencyMs;

    /**
     * 会话延迟（毫秒）。
     *
     * <p>已建立连接上的一次往返耗时：jdbc 跑 {@code SELECT 1}、redis 跑 {@code PING}，
     * 其余类型做一次 TCP 探活。由「测试连接」回写。</p>
     */
    @TableField("datasource_session_latency_ms")
    @Schema(description = "会话延迟(毫秒)", example = "2")
    private Long datasourceSessionLatencyMs;

    /**
     * 最近一次连通性测试时间
     */
    @TableField("datasource_last_test_time")
    @Schema(description = "最近一次测试时间", example = "2026-09-27T10:00:00")
    private LocalDateTime datasourceLastTestTime;

    /**
     * 最近一次测试是否连通，0 失败、1 成功
     */
    @TableField("datasource_last_test_ok")
    @Schema(description = "最近一次测试是否连通 0失败 1成功", example = "1")
    private Integer datasourceLastTestOk;

    /**
     * 创建时间
     */
    @TableField("datasource_create_time")
    @Schema(description = "创建时间", example = "2026-09-21T10:00:00")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("datasource_update_time")
    @Schema(description = "更新时间", example = "2026-09-21T10:00:00")
    private LocalDateTime updateTime;
}