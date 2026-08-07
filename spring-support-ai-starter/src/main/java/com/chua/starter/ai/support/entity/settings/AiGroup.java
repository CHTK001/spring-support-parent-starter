package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@TableName("sys_ai_group")
@Schema(name = "模型分组")
public class AiGroup implements Serializable {

    /**
     * 分组主键 ID
     */
    /**
     * 分组主键 ID
     */
    @TableId(value = "sys_ai_group_id", type = IdType.AUTO)
    @Schema(description = "分组主键 ID")
    private Long groupId;

    /**
     * 分组名称
     */
    /**
     * 分组名称
     */
    @TableField("sys_ai_group_name")
    @Schema(description = "分组名称")
    private String groupName;

    /**
     * 分组描述
     */
    /**
     * 分组描述
     */
    @TableField("sys_ai_group_desc")
    @Schema(description = "分组描述")
    private String groupDescription;

    /**
     * 关联模型列表（JSON 数组字符串）
     */
    /**
     * 关联模型列表(JSON)
     */
    @TableField("sys_ai_group_models")
    @Schema(description = "关联模型列表(JSON)")
    private String groupModels;

    /**
     * 绑定 AppKey ID 列表（JSON 数组字符串）
     */
    /**
     * 绑定 AppKey ID 列表(JSON)
     */
    @TableField("sys_ai_group_app_key_ids")
    @Schema(description = "绑定 AppKey ID 列表(JSON)")
    private String groupAppKeyIds;

    /**
     * 是否启用
     */
    /**
     * 是否启用
     */
    @TableField("sys_ai_group_enabled")
    @Schema(description = "是否启用")
    private Boolean groupEnabled;

    /**
     * 创建时间
     */
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime groupCreateTime;

    /**
     * 更新时间
     */
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime groupUpdateTime;

    /**
     * 逻辑删除标记
     */
    /**
     * 逻辑删除标记
     */
    @TableField("sys_ai_group_delete")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer groupDeleted;
}
