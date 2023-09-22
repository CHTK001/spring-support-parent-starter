package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.constant.NameConstant;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_LEFT_BRACKETS_CHAR;
import static com.chua.common.support.constant.NameConstant.*;
import static com.chua.common.support.utils.ArrayUtils.arraysContains;

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
        SysGenColumn column = new SysGenColumn();
        column.setTabId(sysGenTable.getTabId());
        column.setColQueryType(NameConstant.QUERY_EQ);
        String columnName = null;
        String dataType = null;
        // 设置默认类型
        column.setColJavaType(NameConstant.TYPE_STRING);
        try {
            column.setColColumnName(resultSet.getString("COLUMN_NAME"));
            column.setColColumnType(resultSet.getString("TYPE_NAME"));
            column.setColColumnComment(resultSet.getString("REMARKS"));
            column.setColIsRequired(resultSet.getInt("NULLABLE") == 0 ? "0" : "1");
            column.setColIsIncrement("YES".equalsIgnoreCase(resultSet.getString("IS_AUTOINCREMENT")) ? "1" : "0");
            dataType = getDbType(column.getColColumnType());
            columnName = column.getColColumnName();

        } catch (Exception ignored) {

        }
        if (arraysContains(NameConstant.COLUMNTYPE_STR, dataType) || arraysContains(NameConstant.COLUMNTYPE_TEXT, dataType)) {
            // 字符串长度超过500设置为文本域
            Integer columnLength = getColumnLength(column.getColColumnType());
            String htmlType = columnLength >= 500 || arraysContains(NameConstant.COLUMNTYPE_TEXT, dataType) ? NameConstant.HTML_TEXTAREA : NameConstant.HTML_INPUT;
            column.setColHtmlType(htmlType);
        } else if (arraysContains(NameConstant.COLUMNTYPE_TIME, dataType)) {
            column.setColJavaType(NameConstant.TYPE_JAVA8_DATE);
            column.setColHtmlType(NameConstant.HTML_DATETIME);
        } else if (arraysContains(NameConstant.COLUMNTYPE_NUMBER, dataType)) {
            column.setColHtmlType(NameConstant.HTML_INPUT);

            // 如果是浮点型 统一用BigDecimal
            String[] str = StringUtils.split(StringUtils.substringBetween(column.getColColumnType(), "(", ")"), ",");
            if (str != null && str.length == 2 && Integer.parseInt(str[1]) > 0) {
                column.setColJavaType(NameConstant.TYPE_BIGDECIMAL);
            }
            // 如果是整形
            else if (str != null && str.length == 1 && Integer.parseInt(str[0]) <= 10) {
                column.setColJavaType(NameConstant.TYPE_INTEGER);
            }
            // 长整形
            else {
                column.setColJavaType(NameConstant.TYPE_LONG);
            }
        }
        // BO对象 默认插入勾选
        if (!arraysContains(COLUMNNAME_NOT_ADD, columnName) && !column.isPk()) {
            column.setColIsInsert(REQUIRE);
        }
        // BO对象 默认编辑勾选
        if (!arraysContains(COLUMNNAME_NOT_EDIT, columnName)) {
            column.setColIsEdit(REQUIRE);
        }
        // BO对象 默认是否必填勾选
        if (!arraysContains(COLUMNNAME_NOT_EDIT, columnName)) {
            column.setColIsRequired(REQUIRE);
        }
        // VO对象 默认返回勾选
        if (!arraysContains(COLUMNNAME_NOT_LIST, columnName)) {
            column.setColIsList(REQUIRE);
        }
        // BO对象 默认查询勾选
        if (!arraysContains(COLUMNNAME_NOT_QUERY, columnName) && !column.isPk()) {
            column.setColIsQuery(REQUIRE);
        }

        // 查询字段类型
        if (StringUtils.endsWithIgnoreCase(columnName, "name")) {
            column.setColQueryType(QUERY_LIKE);
        }
        // 状态字段设置单选框
        if (StringUtils.endsWithIgnoreCase(columnName, "status")) {
            column.setColHtmlType(HTML_RADIO);
        }
        // 类型&性别字段设置下拉框
        else if (StringUtils.endsWithIgnoreCase(columnName, "type")
                || StringUtils.endsWithIgnoreCase(columnName, "sex")) {
            column.setColHtmlType(HTML_SELECT);
        }
        // 图片字段设置图片上传控件
        else if (StringUtils.endsWithIgnoreCase(columnName, "image")) {
            column.setColHtmlType(HTML_IMAGE_UPLOAD);
        }
        // 文件字段设置文件上传控件
        else if (StringUtils.endsWithIgnoreCase(columnName, "file")) {
            column.setColHtmlType(HTML_FILE_UPLOAD);
        }
        // 内容字段设置富文本控件
        else if (StringUtils.endsWithIgnoreCase(columnName, "content")) {
            column.setColHtmlType(HTML_EDITOR);
        }
        return column;
    }

    /**
     * 是pk
     *
     * @return boolean
     */
    private boolean isPk() {
        return "1".equals(colIsPk);
    }

    /**
     * 获取数据库类型字段
     *
     * @param columnType 列类型
     * @return 截取后的列类型
     */
    public static String getDbType(String columnType) {
        if (StringUtils.indexOf(columnType, SYMBOL_LEFT_BRACKETS_CHAR) > 0) {
            return StringUtils.substringBefore(columnType, "(");
        } else {
            return columnType;
        }
    }


    /**
     * 获取字段长度
     *
     * @param columnType 列类型
     * @return 截取后的列类型
     */
    public static Integer getColumnLength(String columnType) {
        if (StringUtils.indexOf(columnType, SYMBOL_LEFT_BRACKETS_CHAR) > 0) {
            String length = StringUtils.substringBetween(columnType, "(", ")");
            return Integer.valueOf(length);
        } else {
            return 0;
        }
    }
}