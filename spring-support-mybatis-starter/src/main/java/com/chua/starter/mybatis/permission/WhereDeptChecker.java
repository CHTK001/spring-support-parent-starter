package com.chua.starter.mybatis.permission;

import lombok.Getter;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * where校验是否存在机构ID
 *
 * @author CH
 */
public class WhereDeptChecker {
    private final Expression where;
    private final String tableAlias;
    private final String columnName;
    @Getter
    private Table currentTable;

    public WhereDeptChecker(Expression where, String tableAlias, String columnName) {
        this.where = where;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    /**
     * 是否包含部门ID
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
     * 是否包含部门ID
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
     * 是否包含部门ID
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
