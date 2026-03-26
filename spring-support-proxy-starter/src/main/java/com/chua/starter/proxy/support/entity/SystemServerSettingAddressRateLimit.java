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
 * AddressRateLimitServletFilter 专用配置表
 * 字段前缀：addressRateLimit
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("地址级别限流配置")
@Schema(name = "地址限流配置")
@TableName("proxy_server_setting_address_rate_limit")
public class SystemServerSettingAddressRateLimit extends SysBase {

    /** 主键ID */
    @TableId(value = "proxy_server_setting_address_rate_limit_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = { })
    private Integer systemServerSettingAddressRateLimitId;

    /** 所属服务器ID */
    @TableField("address_rate_limit_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer addressRateLimitServerId;

    /** 地址(接口/路径) */
    @TableField("address_rate_limit_address")
    @ApiModelProperty("地址(接口/路径)")
    @Schema(description = "地址(接口/路径)")
    @Size(max = 200)
    private String addressRateLimitAddress;

    /** 每秒请求阈值 */
    @TableField("address_rate_limit_qps")
    @ApiModelProperty("每秒请求阈值")
    @Schema(description = "每秒请求阈值")
    private Integer addressRateLimitQps;

    /**
     * 类型: RATE_LIMIT/WHITELIST/BLACKLIST
     */
    @TableField("address_rate_limit_type")
    @ApiModelProperty("类型: RATE_LIMIT/WHITELIST/BLACKLIST")
    @Schema(description = "类型: RATE_LIMIT/WHITELIST/BLACKLIST")
    @Size(max = 32)
    private String addressRateLimitType;

    /** 是否启用 */
    @TableField("address_rate_limit_enabled")
    @ApiModelProperty("是否启用")
    @Schema(description = "是否启用")
    private Boolean addressRateLimitEnabled;

    /**
     * 关联的系统配置ID（SystemServerSettingId，用于同一服务器多个同类Filter区分）
     */
    @TableField("address_rate_limit_setting_id")
    @ApiModelProperty("关联的系统配置ID(SystemServerSettingId)")
    @Schema(description = "关联的系统配置ID(SystemServerSettingId)")
    private Integer addressRateLimitSettingId;
}





