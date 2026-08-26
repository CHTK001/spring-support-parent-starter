package com.chua.starter.server.support.entity;

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
 * 服务器主机实体
 * <p>
 * 对应表 {@code ms_server_host}，存储服务器资产台账主数据。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务器主机")
@TableName("ms_server_host")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerHost extends SysBase {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 主机名称
     */
    @TableField("ms_server_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String serverHostName;

    /**
     * 主机编码（唯一标识）
     */
    @TableField("ms_server_host_code")
    @Schema(description = "主机编码", example = "app-01")
    private String serverHostCode;

    /**
     * 主机地址
     */
    @TableField("ms_server_host_address")
    @Schema(description = "主机地址", example = "124.221.230.112")
    private String serverHostAddress;

    /**
     * SSH 端口
     */
    @TableField("ms_server_host_port")
    @Schema(description = "SSH端口", example = "22")
    private Integer serverHostPort;

    /**
     * 操作系统类型
     */
    @TableField("ms_server_host_os_type")
    @Schema(description = "操作系统类型: LINUX / WINDOWS / MACOS", example = "LINUX")
    private String serverHostOsType;

    /**
     * 操作系统架构
     */
    @TableField("ms_server_host_os_arch")
    @Schema(description = "操作系统架构", example = "x86_64")
    private String serverHostOsArch;

    /**
     * 所属分组 ID
     */
    @TableField("ms_server_group_id")
    @Schema(description = "所属分组ID", example = "1")
    private Long msServerGroupId;

    /**
     * 标签集合（JSON 数组字符串）
     */
    @TableField("ms_server_host_tags")
    @Schema(description = "标签集合(JSON数组)", example = "[\"生产\",\"核心\"]")
    private String serverHostTags;

    /**
     * 启用状态
     */
    @TableField("ms_server_host_status")
    @Schema(description = "启用状态 1启用 0停用", example = "1")
    private Integer serverHostStatus;

    /**
     * 在线状态
     */
    @TableField("ms_server_host_online")
    @Schema(description = "在线状态 1在线 0离线 -1未知", example = "-1")
    private Integer serverHostOnline;

    /**
     * 最近在线时间
     */
    @TableField("ms_server_host_online_time")
    @Schema(description = "最近在线时间")
    private java.time.LocalDateTime serverHostOnlineTime;

    /**
     * 探针标识（为后续 Agent 上报预留，服务端生成）
     */
    @TableField("ms_server_host_agent_key")
    @Schema(description = "探针标识(32位)", example = "8f14e45fceea167a5a36dedd4bea2543")
    private String serverHostAgentKey;

    /**
     * 描述
     */
    @TableField("ms_server_host_description")
    @Schema(description = "描述", example = "核心业务服务器")
    private String serverHostDescription;
}
