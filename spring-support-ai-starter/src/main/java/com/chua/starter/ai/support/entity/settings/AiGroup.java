package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型分组表，将模型与 AppKey 按业务/团队维度聚合。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_group")
@ApiModel("模型分组")
public class AiGroup implements Serializable {

    /**
     * 分组主键 ID
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("分组主键 ID")
    private Long groupId;

    /**
     * 分组名称
     */
    @TableField("group_name")
    @ApiModelProperty("分组名称")
    private String groupName;

    /**
     * 分组描述
     */
    @TableField("group_description")
    @ApiModelProperty("分组描述")
    private String groupDescription;

    /**
     * 分组关联模型列表（JSON 字符串）
     */
    @TableField("group_models")
    @ApiModelProperty("分组关联模型列表(JSON)")
    private String groupModels;

    /**
     * 分组绑定 AppKey ID 列表（JSON 数组字符串）
     */
    @TableField("group_app_key_ids")
    @ApiModelProperty("分组绑定 AppKey ID 列表(JSON)")
    private String groupAppKeyIds;

    /**
     * 是否启用
     */
    @TableField("group_enabled")
    @ApiModelProperty("是否启用")
    private Boolean groupEnabled;

    /**
     * 创建时间
     */
    @TableField(value = "group_create_time", fill = FieldFill.INSERT)
    @ApiModelProperty("创建时间")
    private LocalDateTime groupCreateTime;

    /**
     * 更新时间
     */
    @TableField(value = "group_update_time", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty("更新时间")
    private LocalDateTime groupUpdateTime;

    /**
     * 逻辑删除标记
     */
    @TableField("group_deleted")
    @TableLogic
    @ApiModelProperty("逻辑删除标记")
    private Integer groupDeleted;
}