package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采集结果字段定义。
 *
 * <p>决定结果表格列顺序、同键覆盖的业务主键，以及导出到业务表时的列与类型。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_field")
@Schema(description = "数据采集-结果字段定义")
public class CollectField extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_field_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectFieldId;

    /**
     * 所属流程ID。
     */
    @Schema(description = "所属流程ID")
    private Integer collectFieldFlowId;

    /**
     * 产出该字段的节点ID，空表示流程级。
     */
    @Schema(description = "产出节点ID")
    private String collectFieldNodeId;

    /**
     * 字段名。
     */
    @Schema(description = "字段名")
    private String collectFieldName;

    /**
     * 列标题。
     */
    @Schema(description = "列标题")
    private String collectFieldLabel;

    /**
     * 字段类型 STRING/INT/LONG/DECIMAL/DATE/TEXT。
     */
    @Schema(description = "字段类型")
    private String collectFieldType;

    /**
     * 是否业务主键，1 表示同键覆盖写入。
     */
    @Schema(description = "是否业务主键")
    private Integer collectFieldPrimary;

    /**
     * 导出建表时的字符串长度，0 取默认。
     */
    @Schema(description = "字符串长度")
    private Integer collectFieldLength;

    /**
     * 列顺序。
     */
    @Schema(description = "列顺序")
    private Integer collectFieldSort;

    /**
     * 是否启用，0 隐藏 1 显示。
     */
    @Schema(description = "是否启用")
    private Integer collectFieldEnabled;
}
