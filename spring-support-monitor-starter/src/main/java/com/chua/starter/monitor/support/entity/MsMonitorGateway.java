package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 监控网关实体(Guacamole)
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控网关")
@TableName("ms_monitor_gateway")
public class MsMonitorGateway extends SysBase {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 关联监控资产 id
     */
    @TableField("ms_monitor_gateway_monitor_id")
    @Schema(description = "关联监控资产 id", example = "1")
    private Long monitorId;

    /**
     * 网关名称
     */
    @TableField("ms_monitor_gateway_name")
    @Schema(description = "网关名称", example = "guacamole-default")
    private String gatewayName;

    /**
     * 网关类型
     */
    @TableField("ms_monitor_gateway_type")
    @Schema(description = "网关类型: GUACAMOLE / SSH / WEB", example = "GUACAMOLE")
    private String gatewayType;

    /**
     * 网关主机
     */
    @TableField("ms_monitor_gateway_host")
    @Schema(description = "网关主机", example = "192.168.1.20")
    private String gatewayHost;

    /**
     * 网关端口
     */
    @TableField("ms_monitor_gateway_port")
    @Schema(description = "网关端口", example = "4822")
    private Integer gatewayPort;

    /**
     * 连接配置 URL
     */
    @TableField("ms_monitor_gateway_config_url")
    @Schema(description = "连接配置 URL",
            example = "guacamole://192.168.1.20/connect?host=192.168.1.10&port=22")
    private String gatewayConfigUrl;

    /**
     * 用户名
     */
    @TableField("ms_monitor_gateway_username")
    @Schema(description = "用户名", example = "guacadmin")
    private String gatewayUsername;

    /**
     * 密码密文
     */
    @TableField("ms_monitor_gateway_password")
    @Schema(description = "密码密文", example = "***")
    private String gatewayPassword;

    /**
     * 是否启用
     */
    @TableField("ms_monitor_gateway_enabled")
    @Schema(description = "是否启用 1启用 0禁用", example = "1")
    private Integer gatewayEnabled;

    /**
     * 备注
     */
    @TableField("ms_monitor_gateway_remark")
    @Schema(description = "备注", example = "主机默认 Guacamole 网关")
    private String remark;
}
