package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 低代码-页面出参，列表不返回 Schema 正文，只有详情返回。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "低代码—页面出参")
public class LowFormPageVO {

    /**
     * 页面ID。
     */
    @Schema(description = "页面ID")
    private Integer lowPageId;

    /**
     * 页面编码。
     */
    @Schema(description = "页面编码")
    private String lowPageCode;

    /**
     * 页面名称。
     */
    @Schema(description = "页面名称")
    private String lowPageName;

    /**
     * 页面描述。
     */
    @Schema(description = "页面描述")
    private String lowPageDesc;

    /**
     * 所属目录ID。
     */
    @Schema(description = "所属目录ID")
    private Integer lowPageDirectoryId;

    /**
     * 所属目录名称。
     */
    @Schema(description = "所属目录名称")
    private String lowPageDirectoryName;

    /**
     * 状态。
     */
    @Schema(description = "状态 - 0:停用, 1:启用")
    private Integer lowPageStatus;

    /**
     * 发布状态。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowPagePublishStatus;

    /**
     * 版本。
     */
    @Schema(description = "版本")
    private Integer lowPageVersion;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowPageRemark;

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
