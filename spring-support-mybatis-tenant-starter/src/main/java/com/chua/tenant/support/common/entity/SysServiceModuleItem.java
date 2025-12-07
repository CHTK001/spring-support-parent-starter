package com.chua.tenant.support.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 服务与模块关联实体
 * 用于管理服务与模块的多对多关系
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@ApiModel(description = "服务模块关联")
@Schema(description = "服务模块关联")
@Data
@TableName(value = "sys_service_module_item")
public class SysServiceModuleItem {

    /**
     * 关联ID
     */
    @TableId(value = "sys_service_module_item_id", type = IdType.AUTO)
    @ApiModelProperty(value = "关联ID")
    @Schema(description = "关联ID")
    private Integer sysServiceModuleItemId;

    /**
     * 服务ID
     */
    @TableField(value = "sys_service_id")
    @ApiModelProperty(value = "服务ID")
    @Schema(description = "服务ID")
    private Integer sysServiceId;

    /**
     * 服务模块ID
     */
    @TableField(value = "sys_service_module_id")
    @ApiModelProperty(value = "服务模块ID")
    @Schema(description = "服务模块ID")
    private Integer sysServiceModuleId;
}
