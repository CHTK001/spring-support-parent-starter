package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 低代码-形式出参，列表不返回 Schema 正文，只有详情返回。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "低代码—表单出参")
public class LowFormVO {

    /**
     * 表单ID。
     */
    @Schema(description = "表单ID")
    private Integer lowFormId;

    /**
     * 表单编码。
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
     * 所属目录名称。
     */
    @Schema(description = "所属目录名称")
    private String lowFormDirectoryName;

    /**
     * 状态。
     */
    @Schema(description = "状态 - 0:停用, 1:启用")
    private Integer lowFormStatus;

    /**
     * 发布状态。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowFormPublishStatus;

    /**
     * 版本。
     */
    @Schema(description = "版本")
    private Integer lowFormVersion;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowFormRemark;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private String createTime;

    /**
     * 更新时间。
     */
    @Schema(description = "更新时间")
    private String updateTime;
}
