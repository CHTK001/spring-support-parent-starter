package com.chua.starter.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码-页面定义（pageId 内部自增，pageCode 为系统成本唯一，人工录入页面编码）。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("low_page")
@Schema(description = "低代码—页面定义")
public class LowFormPage extends SysBase {

    /**
     * 页面ID，内部标识，自增。
     */
    @TableId(value = "low_page_id", type = IdType.AUTO)
    @Schema(description = "页面ID")
    private Integer lowPageId;

    /**
     * 页面编码，唯一（人工录入，后台强约束唯一）。
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
     * 页面 Schema（JSON）。
     */
    @Schema(description = "页面 Schema")
    private String lowPageSchema;

    /**
     * 页面状态 - 0:停用, 1:启用。
     */
    @Schema(description = "页面状态 - 0:停用, 1:启用")
    private Integer lowPageStatus;

    /**
     * 发布状态 - 0:未发布, 1:已发布。
     */
    @Schema(description = "发布状态 - 0:未发布, 1:已发布")
    private Integer lowPagePublishStatus;

    /**
     * 版本，每次发布 +1。
     */
    @Schema(description = "版本，每次发布+1")
    private Integer lowPageVersion;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowPageRemark;
}
