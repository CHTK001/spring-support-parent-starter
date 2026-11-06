package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采集分类。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_category")
@Schema(description = "数据采集-分类")
public class CollectCategory extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_category_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectCategoryId;

    /**
     * 分类名称。
     */
    @Schema(description = "分类名称")
    private String collectCategoryName;

    /**
     * 父分类ID，顶级为 0。
     */
    @Schema(description = "父分类ID")
    private Integer collectCategoryPid;

    /**
     * 分类路径链。
     */
    @Schema(description = "分类路径链")
    private String collectCategoryTreeId;

    /**
     * 排序，越小越靠前。
     */
    @Schema(description = "排序")
    private Integer collectCategorySort;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String collectCategoryRemark;
}
