package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 低代码-页面版本出参。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "低代码—页面版本出参")
public class LowFormPageVersionVO {

    /**
     * 版本ID。
     */
    @Schema(description = "版本ID")
    private Integer lowPageVersionId;

    /**
     * 所属页面ID。
     */
    @Schema(description = "所属页面ID")
    private Integer lowPageId;

    /**
     * 所属页面编码。
     */
    @Schema(description = "所属页面编码")
    private String lowPageCode;

    /**
     * 版本号。
     */
    @Schema(description = "版本号")
    private Integer lowPageVersionNo;

    /**
     * 版本 Schema。
     */
    @Schema(description = "版本 Schema")
    private String lowPageVersionSchema;

    /**
     * 状态。
     */
    @Schema(description = "状态 - 0:停用, 1:启用")
    private Integer lowPageVersionStatus;

    /**
     * 发布状态。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowPageVersionPublishStatus;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowPageVersionRemark;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private String createTime;
}
