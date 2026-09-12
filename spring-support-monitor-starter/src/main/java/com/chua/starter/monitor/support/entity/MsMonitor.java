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
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控资产")
@TableName("ms_monitor")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsMonitor extends SysBase {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    /** 标识 */
    private Long id;

    /**
     * 名称
     */
    @TableField("ms_monitor_name")
    @Schema(description = "名称", example = "redis-prod-01")
    /** 监控名称 */
    private String monitorName;

    /**
     * 主机地址 / IP
     */
    @TableField("ms_monitor_host")
    @Schema(description = "主机地址", example = "192.168.1.10")
    /** 监控主机 */
    private String monitorHost;

    /**
     * 连接端口
     */
    @TableField("ms_monitor_port")
    @Schema(description = "连接端口", example = "22")
    /** 监控端口 */
    private Integer monitorPort;

    /**
     * 服务类型
     */
    @TableField("ms_monitor_service_type")
    @Schema(description = "服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ",
            example = "REDIS")
    /** 服务类型 */
    private String serviceType;

    /**
     * 服务器子类型
     */
    @TableField("ms_monitor_server_type")
    @Schema(description = "服务器子类型(仅 SERVER): LINUX / WINDOWS / MACOS / JVM",
            example = "LINUX")
    /** 服务器类型 */
    private String serverType;

    /**
     * 操作系统版本
     */
    @TableField("ms_monitor_server_os_version")
    @Schema(description = "操作系统版本", example = "CentOS 7.9")
    /** 服务器OS版本 */
    private String serverOsVersion;

    /**
     * 分组
     */
    @TableField("ms_monitor_group")
    @Schema(description = "分组", example = "default")
    /** 监控分组 */
    private String monitorGroup;

    /**
     * 描述
     */
    @TableField("ms_monitor_description")
    @Schema(description = "描述", example = "生产 Redis")
    /** 监控描述 */
    private String monitorDescription;

    /**
     * 是否启用
     */
    @TableField("ms_monitor_enabled")
    @Schema(description = "是否启用 1启用 0禁用", example = "1")
    /** 监控是否启用 */
    private Integer monitorEnabled;

    /**
      * 探针标识（智能体 推送鉴权键，服务端生成或录入）
     */
    @TableField("ms_monitor_agent_key")
    @Schema(description = "探针标识(Agent推送鉴权)", example = "8f14e45fceea167a5a36dedd4bea2543")
    private String monitorAgentKey; // 监控智能体钥匙

    /**
       * 关联服务器主机 标识（ms_服务端_主机.标识，用于联动服务器台账配置）
     */
    @TableField("ms_server_host_id")
    @Schema(description = "关联服务器主机ID", example = "1")
    private Long msServerHostId; // ms服务端主机标识
}
