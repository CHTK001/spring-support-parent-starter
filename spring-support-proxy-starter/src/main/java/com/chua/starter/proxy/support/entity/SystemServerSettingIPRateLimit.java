package com.chua.starter.proxy.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * IPRateLimitServletFilter 专用配置表
 * 字段前缀：ipRateLimit
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("IP级别限流配置")
@Schema(name = "IP限流配置")
@TableName("proxy_server_setting_ip_rate_limit")
public class SystemServerSettingIPRateLimit extends SysBase {

    /** 主键ID */
    @TableId(value = "proxy_server_setting_ip_rate_limit_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = { })
    private Integer systemServerSettingIpRateLimitId;

    /** 所属服务器ID */
    @TableField("ip_rate_limit_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer ipRateLimitServerId;

    /** IP地址(可为CIDR) */
    @TableField("ip_rate_limit_ip")
    @ApiModelProperty("IP地址(支持CIDR)")
    @Schema(description = "IP地址(支持CIDR)")
    @Size(max = 100)
    private String ipRateLimitIp;

    /** 每秒请求阈值 */
    @TableField("ip_rate_limit_qps")
    @ApiModelProperty("每秒请求阈值")
    @Schema(description = "每秒请求阈值")
    private Integer ipRateLimitQps;

    /**
     * 类型: RATE_LIMIT/WHITELIST/BLACKLIST
     */
    @TableField("ip_rate_limit_type")
    @ApiModelProperty("类型: RATE_LIMIT/WHITELIST/BLACKLIST")
    @Schema(description = "类型: RATE_LIMIT/WHITELIST/BLACKLIST")
    @Size(max = 32)
    private String ipRateLimitType;

    /** 是否启用 */
    @TableField("ip_rate_limit_enabled")
    @ApiModelProperty("是否启用")
    @Schema(description = "是否启用")
    private Boolean ipRateLimitEnabled;

    /**
     * 关联的系统配置ID（SystemServerSettingId，用于同一服务器多个同类Filter区分）
     */
    @TableField("ip_rate_limit_setting_id")
    @ApiModelProperty("关联的系统配置ID(SystemServerSettingId)")
    @Schema(description = "关联的系统配置ID(SystemServerSettingId)")
    private Integer ipRateLimitSettingId;
}





