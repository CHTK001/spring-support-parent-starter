package com.chua.starter.proxy.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统服务器配置项详情表实体类
 *
 * @author CH
 * @since 2025/01/07
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "系统服务器配置项详情表")
@Schema(description = "系统服务器配置项详情表")
@Data
@TableName(value = "proxy_server_setting_item")
public class SystemServerSettingItem extends SysBase {

    /**
     * 系统服务器配置项ID
     */
    @TableId(value = "proxy_server_setting_item_id", type = IdType.AUTO)
    @ApiModelProperty(value = "系统服务器配置项ID")
    @Schema(description = "系统服务器配置项ID")
    @NotNull(message = "系统服务器配置项ID不能为空", groups = {UpdateGroup.class})
    private Integer systemServerSettingItemId;

    /**
     * 关联的配置ID (外键)
     */
    @TableField(value = "proxy_server_setting_item_setting_id")
    @ApiModelProperty(value = "关联的配置ID")
    @Schema(description = "关联的配置ID")
    @NotNull(message = "关联的配置ID不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer systemServerSettingItemSettingId;

    /**
     * 配置项名称
     */
    @TableField(value = "proxy_server_setting_item_name")
    @ApiModelProperty(value = "配置项名称")
    @Schema(description = "配置项名称")
    @NotBlank(message = "配置项名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Size(max = 255, message = "配置项名称最大长度要小于 255")
    private String systemServerSettingItemName;

    /**
     * 配置项值
     */
    @TableField(value = "proxy_server_setting_item_value")
    @ApiModelProperty(value = "配置项值")
    @Schema(description = "配置项值")
    private String systemServerSettingItemValue;

    /**
     * 配置项描述
     */
    @TableField(value = "proxy_server_setting_item_description")
    @ApiModelProperty(value = "配置项描述")
    @Schema(description = "配置项描述")
    @Size(max = 500, message = "配置项描述最大长度要小于 500")
    private String systemServerSettingItemDescription;

    /**
     * 配置项类型 (string, number, boolean, json等)
     */
    @TableField(value = "proxy_server_setting_item_type")
    @ApiModelProperty(value = "配置项类型")
    @Schema(description = "配置项类型")
    @Size(max = 50, message = "配置项类型最大长度要小于 50")
    private String systemServerSettingItemType;

    /**
     * 是否必填 0:否 1:是
     */
    @TableField(value = "proxy_server_setting_item_required")
    @ApiModelProperty(value = "是否必填")
    @Schema(description = "是否必填")
    private Boolean systemServerSettingItemRequired;

    /**
     * 默认值
     */
    @TableField(value = "proxy_server_setting_item_default_value")
    @ApiModelProperty(value = "默认值")
    @Schema(description = "默认值")
    private String systemServerSettingItemDefaultValue;

    /**
     * 验证规则 (正则表达式或其他验证规则)
     */
    @TableField(value = "proxy_server_setting_item_validation_rule")
    @ApiModelProperty(value = "验证规则")
    @Schema(description = "验证规则")
    @Size(max = 500, message = "验证规则最大长度要小于 500")
    private String systemServerSettingItemValidationRule;

    /**
     * 排序字段
     */
    @TableField(value = "proxy_server_setting_item_order")
    @ApiModelProperty(value = "排序字段")
    @Schema(description = "排序字段")
    private Integer systemServerSettingItemOrder;
}




