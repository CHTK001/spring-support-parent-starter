package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集分类出参，children 组树。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "数据采集-分类出参")
public class CollectCategoryVO {

    /**
     * 主键ID。
     */
    @Schema(description = "主键ID")
    private Integer collectCategoryId;

    /**
     * 分类名称。
     */
    @Schema(description = "分类名称")
    private String collectCategoryName;

    /**
     * 父分类ID。
     */
    @Schema(description = "父分类ID")
    private Integer collectCategoryPid;

    /**
     * 排序。
     */
    @Schema(description = "排序")
    private Integer collectCategorySort;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String collectCategoryRemark;

    /**
     * 该分类下的流程数。
     */
    @Schema(description = "流程数")
    private Integer flowCount;

    /**
     * 子分类。
     */
    @Schema(description = "子分类")
    private List<CollectCategoryVO> children = new ArrayList<>();
}
