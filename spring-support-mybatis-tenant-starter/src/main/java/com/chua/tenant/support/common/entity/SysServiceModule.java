package com.chua.tenant.support.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 服务模块实体
 * 用于管理服务下的功能模块
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@ApiModel(description = "服务模块")
@Schema(description = "服务模块")
@Data
@TableName(value = "sys_service_module")
public class SysServiceModule {

    /**
     * 服务模块ID
     */
    @TableId(value = "sys_service_module_id", type = IdType.AUTO)
    @ApiModelProperty(value = "服务模块ID")
    @Schema(description = "服务模块ID")
    @NotNull(message = "服务模块ID不能为空", groups = {UpdateGroup.class})
    private Integer sysServiceModuleId;

    /**
     * 服务模块名称
     */
    @TableField(value = "sys_service_module_name")
    @ApiModelProperty(value = "服务模块名称")
    @Schema(description = "服务模块名称")
    @NotBlank(message = "服务模块名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String sysServiceModuleName;

    /**
     * 服务模块编码
     */
    @TableField(value = "sys_service_module_code")
    @ApiModelProperty(value = "服务模块编码")
    @Schema(description = "服务模块编码")
    @NotBlank(message = "服务模块编码不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String sysServiceModuleCode;

    /**
     * 服务模块描述
     */
    @TableField(value = "sys_service_module_desc")
    @ApiModelProperty(value = "服务模块描述")
    @Schema(description = "服务模块描述")
    private String sysServiceModuleDesc;

    /**
     * 服务模块排序
     */
    @TableField(value = "sys_service_module_sort")
    @ApiModelProperty(value = "服务模块排序")
    @Schema(description = "服务模块排序")
    private Integer sysServiceModuleSort;

    /**
     * 删除标记
     * 0: 未删除, 1: 已删除
     */
    @TableField(value = "sys_service_module_delete")
    @ApiModelProperty(value = "删除标记")
    @Schema(description = "删除标记: 0-未删除, 1-已删除")
    private Integer sysServiceModuleDelete;

    /**
     * 服务模块状态
     * 0: 启用, 1: 禁用
     */
    @TableField(value = "sys_service_module_status")
    @ApiModelProperty(value = "服务模块状态")
    @Schema(description = "服务模块状态: 0-启用, 1-禁用")
    private Integer sysServiceModuleStatus;
}
