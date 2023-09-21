package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * sys gen柱
 *
 * @author CH
 * @since 2023/09/21
 */
@Data
@TableName(value = "sys_gen_column")
public class SysGenColumn implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "col_id", type = IdType.AUTO)
    private Integer colId;

    /**
     * 表ID
     */
    @TableField(value = "tab_id")
    private Integer tabId;

    /**
     * 列名称
     */
    @TableField(value = "col_column_name")
    private String colColumnName;

    /**
     * 列描述
     */
    @TableField(value = "col_column_comment")
    private String colColumnComment;

    /**
     * 列类型
     */
    @TableField(value = "col_column_type")
    private String colColumnType;

    /**
     * JAVA类型
     */
    @TableField(value = "col_java_type")
    private String colJavaType;

    /**
     * JAVA字段名
     */
    @TableField(value = "col_java_field")
    private String colJavaField;

    /**
     * 是否主键（1是）
     */
    @TableField(value = "col_is_pk")
    private String colIsPk;

    /**
     * 是否自增（1是）
     */
    @TableField(value = "col_is_increment")
    private String colIsIncrement;

    /**
     * 是否必填（1是）
     */
    @TableField(value = "col_is_required")
    private String colIsRequired;

    /**
     * 是否为插入字段（1是）
     */
    @TableField(value = "col_is_insert")
    private String colIsInsert;

    /**
     * 是否编辑字段（1是）
     */
    @TableField(value = "col_is_edit")
    private String colIsEdit;

    /**
     * 是否列表字段（1是）
     */
    @TableField(value = "col_is_list")
    private String colIsList;

    /**
     * 是否查询字段（1是）
     */
    @TableField(value = "col_is_query")
    private String colIsQuery;

    /**
     * 查询方式（等于、不等于、大于、小于、范围）
     */
    @TableField(value = "col_query_type")
    private String colQueryType;

    /**
     * 显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）
     */
    @TableField(value = "col_html_type")
    private String colHtmlType;

    /**
     * 字典类型
     */
    @TableField(value = "col_dict_type")
    private String colDictType;

    /**
     * 排序
     */
    @TableField(value = "col_sort")
    private Integer colSort;

    private static final long serialVersionUID = 1L;

    /**
     * 创建sys-gen列
     *
     * @param sysGenTable sys-gen表
     * @param tableName   表名称
     * @param resultSet   结果集
     * @return {@link com.chua.starter.gen.support.entity.SysGenColumn}
     */
    public static com.chua.starter.gen.support.entity.SysGenColumn createSysGenColumn(SysGenTable sysGenTable, String tableName, ResultSet resultSet) throws SQLException {
        SysGenColumn sysGenColumn = new SysGenColumn();
        try {
            sysGenColumn.setTabId(sysGenTable.getTabId());
            sysGenColumn.setColColumnName(resultSet.getString("COLUMN_NAME"));
            sysGenColumn.setColColumnType(resultSet.getString("TYPE_NAME"));
            sysGenColumn.setColColumnComment(resultSet.getString("REMARKS"));
            sysGenColumn.setColIsRequired(resultSet.getInt("NULLABLE") == 0 ? "0" : "1");
            sysGenColumn.setColIsIncrement("YES".equalsIgnoreCase(resultSet.getString("IS_AUTOINCREMENT")) ? "1" : "0");
        } catch (Exception ignored) {
        }
        return sysGenColumn;
    }
}