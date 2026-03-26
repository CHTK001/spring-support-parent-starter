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
 * ServiceDiscoveryServletFilter 专用配置表
 * 字段前缀：serviceDiscovery
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务发现过滤器配置")
@Schema(name = "ServiceDiscovery配置")
@TableName("proxy_server_setting_service_discovery")
public class SystemServerSettingServiceDiscovery extends SysBase {

    /** 主键ID */
    @TableId(value = "proxy_server_setting_service_discovery_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = { })
    private Integer systemServerSettingServiceDiscoveryId;

    /** 所属服务器ID */
    @TableField("service_discovery_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer serviceDiscoveryServerId;

    /** 模式: MONITOR/SPRING/TABLE/HAZELCAST */
    @TableField("service_discovery_mode")
    @ApiModelProperty("服务发现模式: MONITOR/SPRING/TABLE/HAZELCAST")
    @Schema(description = "服务发现模式: MONITOR/SPRING/TABLE/HAZELCAST")
    @Size(max = 50)
    private String serviceDiscoveryMode;

    /** 负载均衡策略（如：weight/round/…） */
    @TableField("service_discovery_balance")
    @ApiModelProperty("默认负载均衡策略")
    @Schema(description = "默认负载均衡策略")
    @Size(max = 50)
    private String serviceDiscoveryBalance;

    /** 当模式为SPRING时的Bean名称 */
    @TableField("service_discovery_bean_name")
    @ApiModelProperty("Spring容器中的ServiceDiscovery Bean名称")
    @Schema(description = "Spring容器中的ServiceDiscovery Bean名称")
    @Size(max = 200)
    private String serviceDiscoveryBeanName;

    /** 是否启用 */
    @TableField("service_discovery_enabled")
    @ApiModelProperty("是否启用")
    @Schema(description = "是否启用")
    private Boolean serviceDiscoveryEnabled;
}





