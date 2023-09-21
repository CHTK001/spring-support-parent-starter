package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author CH
 */
@ApiModel(description = "sys_gen_column")
@Schema
@Data
@TableName(value = "sys_gen_column")
public class SysGenColumn implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "col_id", type = IdType.INPUT)
    @ApiModelProperty(value = "主键")
    @Schema(description = "主键")
    private Integer colId;

    /**
     * 表ID
     */
    @TableField(value = "tab_id")
    @ApiModelProperty(value = "表ID")
    @Schema(description = "表ID")
    private Integer tabId;

    /**
     * 列名称
     */
    @TableField(value = "col_column_name")
    @ApiModelProperty(value = "列名称")
    @Schema(description = "列名称")
    private String colColumnName;

    /**
     * 列描述
     */
    @TableField(value = "col_column_comment")
    @ApiModelProperty(value = "列描述")
    @Schema(description = "列描述")
    private String colColumnComment;

    /**
     * 列类型
     */
    @TableField(value = "col_column_type")
    @ApiModelProperty(value = "列类型")
    @Schema(description = "列类型")
    private String colColumnType;

    /**
     * JAVA类型
     */
    @TableField(value = "col_java_type")
    @ApiModelProperty(value = "JAVA类型")
    @Schema(description = "JAVA类型")
    private String colJavaType;

    /**
     * JAVA字段名
     */
    @TableField(value = "col_java_field")
    @ApiModelProperty(value = "JAVA字段名")
    @Schema(description = "JAVA字段名")
    private String colJavaField;

    /**
     * 是否主键（1是）
     */
    @TableField(value = "col_is_pk")
    @ApiModelProperty(value = "是否主键（1是）")
    @Schema(description = "是否主键（1是）")
    private String colIsPk;

    /**
     * 是否自增（1是）
     */
    @TableField(value = "col_is_increment")
    @ApiModelProperty(value = "是否自增（1是）")
    @Schema(description = "是否自增（1是）")
    private String colIsIncrement;

    /**
     * 是否必填（1是）
     */
    @TableField(value = "col_is_required")
    @ApiModelProperty(value = "是否必填（1是）")
    @Schema(description = "是否必填（1是）")
    private String colIsRequired;

    /**
     * 是否为插入字段（1是）
     */
    @TableField(value = "col_is_insert")
    @ApiModelProperty(value = "是否为插入字段（1是）")
    @Schema(description = "是否为插入字段（1是）")
    private String colIsInsert;

    /**
     * 是否编辑字段（1是）
     */
    @TableField(value = "col_is_edit")
    @ApiModelProperty(value = "是否编辑字段（1是）")
    @Schema(description = "是否编辑字段（1是）")
    private String colIsEdit;

    /**
     * 是否列表字段（1是）
     */
    @TableField(value = "col_is_list")
    @ApiModelProperty(value = "是否列表字段（1是）")
    @Schema(description = "是否列表字段（1是）")
    private String colIsList;

    /**
     * 是否查询字段（1是）
     */
    @TableField(value = "col_is_query")
    @ApiModelProperty(value = "是否查询字段（1是）")
    @Schema(description = "是否查询字段（1是）")
    private String colIsQuery;

    /**
     * 查询方式（等于、不等于、大于、小于、范围）
     */
    @TableField(value = "col_query_type")
    @ApiModelProperty(value = "查询方式（等于、不等于、大于、小于、范围）")
    @Schema(description = "查询方式（等于、不等于、大于、小于、范围）")
    private String colQueryType;

    /**
     * 显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）
     */
    @TableField(value = "col_html_type")
    @ApiModelProperty(value = "显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）")
    @Schema(description = "显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）")
    private String colHtmlType;

    /**
     * 字典类型
     */
    @TableField(value = "col_dict_type")
    @ApiModelProperty(value = "字典类型")
    @Schema(description = "字典类型")
    private String colDictType;

    /**
     * 排序
     */
    @TableField(value = "col_sort")
    @ApiModelProperty(value = "排序")
    @Schema(description = "排序")
    private Integer colSort;

    private static final long serialVersionUID = 1L;

    /**
     * 创建sys-gen列
     *
     * @param sysGenTable sys-gen表
     * @param tableName   表名称
     * @param resultSet   结果集
     * @return {@link SysGenColumn}
     */
    public static SysGenColumn createSysGenColumn(SysGenTable sysGenTable, String tableName, ResultSet resultSet) throws SQLException {
        SysGenColumn sysGenColumn = new SysGenColumn();
        sysGenColumn.setTabId(sysGenColumn.getTabId());
        sysGenColumn.setColColumnName(resultSet.getString("COLUMN_NAME"));
        sysGenColumn.setColColumnType(resultSet.getString("TYPE_NAME"));
        sysGenColumn.setColColumnComment(resultSet.getString("COLUMN_COMMENT"));
        return sysGenColumn;
    }
}