package com.chua.starter.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码-表单定义（formId 内部自增，formCode 为系统成本唯一，人工录入表单编码）。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("low_form")
@Schema(description = "低代码—表单定义")
public class LowForm extends SysBase {

    /**
     * 表单ID，内部标识，自增。
     */
    @TableId(value = "low_form_id", type = IdType.AUTO)
    @Schema(description = "表单ID")
    private Integer lowFormId;

    /**
     * 表单编码，唯一（人工录入，后台强约束唯一）。
     */
    @Schema(description = "表单编码")
    private String lowFormCode;

    /**
     * 表单名称。
     */
    @Schema(description = "表单名称")
    private String lowFormName;

    /**
     * 表单描述。
     */
    @Schema(description = "表单描述")
    private String lowFormDesc;

    /**
     * 所属目录ID。
     */
    @Schema(description = "所属目录ID")
    private Integer lowFormDirectoryId;

    /**
     * 表单 Schema（JSON，设计器产出的完整 Schema）。
     */
    @Schema(description = "表单 Schema")
    private String lowFormSchema;

    /**
     * 表单状态 - 0:停用, 1:启用。
     */
    @Schema(description = "表单状态 - 0:停用, 1:启用")
    private Integer lowFormStatus;

    /**
     * 发布状态 - 0:未发布, 1:已发布。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowFormPublishStatus;

    /**
     * 版本，每次发布 +1。
     */
    @Schema(description = "版本，每次发布+1")
    private Integer lowFormVersion;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowFormRemark;
}
