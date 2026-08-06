package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 监控资产实体
 * <p>
 * 对应表 {@code ms_monitor}, 存储被监控的服务器或中间件的主数据。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控资产")
@TableName("ms_monitor")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsMonitor extends SysBase {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 名称
     */
    @TableField("ms_monitor_name")
    @Schema(description = "名称", example = "redis-prod-01")
    private String monitorName;

    /**
     * 主机地址 / IP
     */
    @TableField("ms_monitor_host")
    @Schema(description = "主机地址", example = "192.168.1.10")
    private String monitorHost;

    /**
     * 连接端口
     */
    @TableField("ms_monitor_port")
    @Schema(description = "连接端口", example = "22")
    private Integer monitorPort;

    /**
     * 服务类型
     */
    @TableField("ms_monitor_service_type")
    @Schema(description = "服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ",
            example = "REDIS")
    private String serviceType;

    /**
     * 服务器子类型
     */
    @TableField("ms_monitor_server_type")
    @Schema(description = "服务器子类型(仅 SERVER): LINUX / WINDOWS / MACOS / JVM",
            example = "LINUX")
    private String serverType;

    /**
     * 操作系统版本
     */
    @TableField("ms_monitor_server_os_version")
    @Schema(description = "操作系统版本", example = "CentOS 7.9")
    private String serverOsVersion;

    /**
     * 分组
     */
    @TableField("ms_monitor_group")
    @Schema(description = "分组", example = "default")
    private String monitorGroup;

    /**
     * 描述
     */
    @TableField("ms_monitor_description")
    @Schema(description = "描述", example = "生产 Redis")
    private String monitorDescription;

    /**
     * 是否启用
     */
    @TableField("ms_monitor_enabled")
    @Schema(description = "是否启用 1启用 0禁用", example = "1")
    private Integer monitorEnabled;
}
