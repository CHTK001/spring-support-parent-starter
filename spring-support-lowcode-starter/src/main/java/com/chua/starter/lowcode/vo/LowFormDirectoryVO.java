package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 低代码-目录出参，children 组树。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "低代码—目录出参")
public class LowFormDirectoryVO {

    /**
     * 目录ID。
     */
    @Schema(description = "目录ID")
    private Integer lowFormDirectoryId;

    /**
     * 目录名称。
     */
    @Schema(description = "目录名称")
    private String lowFormDirectoryName;

    /**
     * 父目录ID。
     */
    @Schema(description = "父目录ID")
    private Integer lowFormDirectoryPid;

    /**
     * 排序。
     */
    @Schema(description = "排序")
    private Integer lowFormDirectorySort;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowFormDirectoryRemark;

    /**
     * 该目录下的表单数。
     */
    @Schema(description = "表单数")
    private Integer formCount;

    /**
     * 该目录下的页面数。
     */
    @Schema(description = "页面数")
    private Integer pageCount;

    /**
     * 子目录。
     */
    @Schema(description = "子目录")
    private List<LowFormDirectoryVO> children = new ArrayList<>();
}
