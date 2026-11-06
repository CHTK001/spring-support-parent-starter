package com.chua.starter.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码-页面版本（每次发布时生成，记录单个版本的 Schema 快照）。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("low_page_version")
@Schema(description = "低代码—页面版本")
public class LowFormPageVersion extends SysBase {

    /**
     * 版本ID，自增。
     */
    @TableId(value = "low_page_version_id", type = IdType.AUTO)
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
     * 版本 Schema（JSON）。
     */
    @Schema(description = "版本 Schema")
    private String lowPageVersionSchema;

    /**
     * 版本状态 - 0:停用, 1:启用。
     */
    @Schema(description = "版本状态 - 0:停用, 1:启用")
    private Integer lowPageVersionStatus;

    /**
     * 发布状态 - 0:未发布, 1:已发布。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowPageVersionPublishStatus;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowPageVersionRemark;
}
