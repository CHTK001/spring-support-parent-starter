package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 低代码-表单版本出参。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "低代码—表单版本出参")
public class LowFormVersionVO {

    /**
     * 版本ID。
     */
    @Schema(description = "版本ID")
    private Integer lowFormVersionId;

    /**
     * 所属表单ID。
     */
    @Schema(description = "所属表单ID")
    private Integer lowFormId;

    /**
     * 所属表单编码。
     */
    @Schema(description = "所属表单编码")
    private String lowFormCode;

    /**
     * 版本号。
     */
    @Schema(description = "版本号")
    private Integer lowFormVersionNo;

    /**
     * 版本 Schema。
     */
    @Schema(description = "版本 Schema")
    private String lowFormVersionSchema;

    /**
     * 状态。
     */
    @Schema(description = "状态 - 0:停用, 1:启用")
    private Integer lowFormVersionStatus;

    /**
     * 发布状态。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowFormVersionPublishStatus;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowFormVersionRemark;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private String createTime;
}
