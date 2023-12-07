package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.constant.NameConstant;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.meta.Column;
import com.chua.common.support.unit.name.NamingCase;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
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
     * 列长度
     */
    @TableField(value = "col_column_length")
    private Integer colColumnLength;

    /**
     * 小数点长度
     */
    @TableField(value = "col_column_decimal")
    private Integer colColumnDecimal;

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
     * @param dialect     dialect
     * @param sysGenTable sys-gen表
     * @param tableName   表名称
     * @param column1     结果集
     * @return {@link SysGenColumn}
     */
    public static SysGenColumn createSysGenColumn(Dialect dialect,
                                                  SysGenTable sysGenTable,
                                                  String tableName,
                                                  Column column1) throws SQLException {
        SysGenColumn column = new SysGenColumn();
        column.setTabId(sysGenTable.getTabId());
        column.setColQueryType(NameConstant.QUERY_EQ);
        String columnName = null;
        String dataType = null;
        // 设置默认类型
        column.setColJavaType(NameConstant.TYPE_STRING);
        column.setColColumnDecimal(0);
        try {
            column.setColColumnName(column1.getName());
            column.setColColumnType(column1.getJdbcType());
            column.setColColumnComment(column1.getComment());
            column.setColIsRequired(!column1.isNullable() ? "1" : "0");
            column.setColIsIncrement(column1.isPk() ? "1" : "0");
            if (CommonConstant.ONE_STR.equals(column.getColIsIncrement())) {
                column.setColIsPk("1");
            }
            int columnSize = column1.getLength();
            column.setColColumnLength(columnSize);
            if (columnSize > 0) {
                Integer decimalDigits = column1.getPrecision();
                if (null != decimalDigits) {
                    column.setColColumnDecimal(decimalDigits);
                }
            }
            dataType = getDbType(column.getColColumnType());
            columnName = column.getColColumnName();
            column.setColJavaField(NamingCase.toCamelCase(column.getColColumnName()));
            column.setColJavaType(column.getColJavaType());
        } catch (Exception ignored) {

        }
        dataType = dataType.toLowerCase();
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
            if (column.getColColumnDecimal() > 0) {
                column.setColJavaType(NameConstant.TYPE_BIGDECIMAL);
            }
            // 如果是整形
            else if (column.getColColumnLength() <= 10) {
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
        // VO对象 默认返回勾选
        if (!arraysContains(COLUMNNAME_NOT_LIST, columnName)) {
            column.setColIsList(REQUIRE);
        }
        // BO对象 默认查询勾选
        if (!arraysContains(COLUMNNAME_NOT_QUERY, columnName) && !column.isPk()) {
            column.setColIsQuery(REQUIRE);
        }

        // 查询字段类型
        if (StringUtils.endsWithIgnoreCase(columnName, NAME)) {
            column.setColQueryType(QUERY_LIKE);
        }
        // 状态字段设置单选框
        if (StringUtils.endsWithIgnoreCase(columnName, STATUS_NAME)) {
            column.setColHtmlType(HTML_RADIO);
        }
        // 类型&性别字段设置下拉框
        else if (StringUtils.endsWithIgnoreCase(columnName, "type")
                || StringUtils.endsWithIgnoreCase(columnName, "sex")) {
            column.setColHtmlType(HTML_SELECT);
        }
        // 图片字段设置图片上传控件
        else if (StringUtils.endsWithIgnoreCase(columnName, NameConstant.IMAGE_NAME)) {
            column.setColHtmlType(HTML_IMAGE_UPLOAD);
        }
        // 文件字段设置文件上传控件
        else if (StringUtils.endsWithIgnoreCase(columnName, FILE)) {
            column.setColHtmlType(HTML_FILE_UPLOAD);
        }
        // 内容字段设置富文本控件
        else if (StringUtils.endsWithIgnoreCase(columnName, NameConstant.CONTENT_NAME)) {
            column.setColHtmlType(HTML_EDITOR);
        }

        if (VERSION.equals(column.getColColumnName())) {
            column.setColJavaType("version");
        }
        if (IS_DELETE.equalsIgnoreCase(column.getColColumnName())) {
            column.setColJavaType("delFlag");
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