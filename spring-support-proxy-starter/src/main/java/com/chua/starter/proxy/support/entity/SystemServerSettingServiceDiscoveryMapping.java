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
 * ServiceDiscovery 默认模式映射明细表
 * 字段前缀：serviceDiscoveryMapping
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务发现映射明细")
@Schema(name = "ServiceDiscovery映射明细")
@TableName("proxy_server_setting_service_discovery_mapping")
public class SystemServerSettingServiceDiscoveryMapping extends SysBase {

    /** 主键ID */
    @TableId(value = "proxy_server_setting_service_discovery_mapping_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = { })
    private Integer systemServerSettingServiceDiscoveryMappingId;

    /** 所属服务器ID */
    @TableField("service_discovery_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer serviceDiscoveryServerId;

    /** 服务名 */
    @TableField("service_discovery_name")
    @ApiModelProperty("服务名称")
    @Schema(description = "服务名称")
    @Size(max = 100)
    private String serviceDiscoveryName;

    /** 服务地址 */
    @TableField("service_discovery_address")
    @ApiModelProperty("服务地址")
    @Schema(description = "服务地址")
    @Size(max = 200)
    private String serviceDiscoveryAddress;

    /** 权重（可选） */
    @TableField("service_discovery_weight")
    @ApiModelProperty("服务权重")
    @Schema(description = "服务权重")
    private Integer serviceDiscoveryWeight;

    /** 是否启用 */
    @TableField("service_discovery_enabled")
    @ApiModelProperty("是否启用")
    @Schema(description = "是否启用")
    private Boolean serviceDiscoveryEnabled;
}





