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

import java.util.List;

/**
 * 服务实体
 * 用于管理系统中的服务定义
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@ApiModel(description = "服务")
@Schema(description = "服务")
@Data
@TableName(value = "sys_service")
public class SysService {

    /**
     * 服务ID
     */
    @TableId(value = "sys_service_id", type = IdType.AUTO)
    @ApiModelProperty(value = "服务ID")
    @Schema(description = "服务ID")
    @NotNull(message = "服务ID不能为空", groups = {UpdateGroup.class})
    private Integer sysServiceId;

    /**
     * 服务名称
     */
    @TableField(value = "sys_service_name")
    @ApiModelProperty(value = "服务名称")
    @Schema(description = "服务名称")
    @NotBlank(message = "服务名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String sysServiceName;

    /**
     * 服务编码
     */
    @TableField(value = "sys_service_code")
    @ApiModelProperty(value = "服务编码")
    @Schema(description = "服务编码")
    @NotBlank(message = "服务编码不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String sysServiceCode;

    /**
     * 服务描述
     */
    @TableField(value = "sys_service_desc")
    @ApiModelProperty(value = "服务描述")
    @Schema(description = "服务描述")
    private String sysServiceDesc;

    /**
     * 服务排序
     */
    @TableField(value = "sys_service_sort")
    @ApiModelProperty(value = "服务排序")
    @Schema(description = "服务排序")
    private Integer sysServiceSort;

    /**
     * 删除标记
     * 0: 未删除, 1: 已删除
     */
    @TableField(value = "sys_service_delete")
    @ApiModelProperty(value = "删除标记")
    @Schema(description = "删除标记: 0-未删除, 1-已删除")
    private Integer sysServiceDelete;

    /**
     * 服务状态
     * 0: 启用, 1: 禁用
     */
    @TableField(value = "sys_service_status")
    @ApiModelProperty(value = "服务状态")
    @Schema(description = "服务状态: 0-启用, 1-禁用")
    private Integer sysServiceStatus;

    /**
     * 关联的服务模块ID列表
     * 非数据库字段，用于前端展示
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "关联的服务模块ID列表")
    @Schema(description = "关联的服务模块ID列表")
    private List<Integer> sysServiceTags;
}
