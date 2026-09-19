package com.chua.starter.mybatis.permission;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * where校验是否存在机构标识
 *
 * @author CH
 * @since 4.0.0
 */
public class WhereDeptChecker {
    /** Where */
    private final Expression where;
    /** 表别名 */
    private final String tableAlias;
    /** 列名称 */
    private final String columnName;
    /** 当前表 */
    private Table currentTable;

    /**
     * 创建 wheredept检查 实例
     * @param where where
     * @param tableAlias 字符串
     * @param tableAlias 字符串
     * @param tableAlias table别名
     * @param columnName column名称
     */
    public WhereDeptChecker(Expression where, String tableAlias, String columnName) {
        this.where = where;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    /**
     * 是否包含部门标识
     *
     * @return 是否存在机构ID
     */
    public boolean check() {
        if (where == null) {
            return false;
        }

        if (!(where instanceof BinaryExpression)) {
            return false;
        }

        return hasDeptId((BinaryExpression) where);
    }

    /**
     * 是否包含部门标识
     *
     * @param expression 表达式
     * @return 是否存在机构ID
     */
    private boolean hasDeptId(BinaryExpression expression) {
        Expression leftExpression = expression.getLeftExpression();
        if (hasDeptId(leftExpression)) {
            return true;
        }

        return hasDeptId(expression.getRightExpression());
    }

    /**
     * 是否包含部门标识
     *
     * @param leftExpression 表达式
     * @return 是否存在机构ID
     */
    private boolean hasDeptId(Expression leftExpression) {
        if (leftExpression instanceof Column column) {
            boolean equals = columnName.equals(column.getColumnName());
            currentTable = column.getTable();
            return equals;
        }

        if (leftExpression instanceof BinaryExpression basicExpression) {
            return hasDeptId(basicExpression);
        }
        return false;
    }

    /**
     * 获取当前表
     *
     * @return 当前表
     */
    public Table getCurrentTable() {
        return currentTable;
    }
}
