package com.chua.starter.mybatis.permission;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.Collections;
import java.util.List;

import static com.chua.common.support.constant.CommonConstant.EMPTY;
import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * select注入机构ID
 *
 * @author CH
 */
public class SelectDeptRegister extends AbstractDeptRegister {

    public SelectDeptRegister(PlainSelect plainSelect, Expression where, CurrentUser user, DataFilterTypeEnum dataPermission, MybatisPlusDataScopeProperties scopeProperties) {
        super(plainSelect, where, user, dataPermission, scopeProperties);
    }

    @Override
    public Expression register() {
        findTableHasDeptId();
        if (null == hasDeptIdTableAlias) {
            if (where == null) {
                plainSelect.setWhere(NO_DATA);
                return NO_DATA;
            }
            AndExpression andExpression = new AndExpression();
            andExpression.setLeftExpression(where);
            andExpression.setRightExpression(NO_DATA);
            plainSelect.setWhere(andExpression);
            return NO_DATA;
        }
        registerPlainSelect(findDeptTable());
        return where;
    }

    /**
     * 查找部门表
     *
     * @return
     */
    private boolean findDeptTable() {
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem == null) {
            return false;
        }
        if (fromItem instanceof Table table) {
            if (table.getName().equalsIgnoreCase(hasDeptIdTableAlias)) {
                return true;
            }
        }

        for (Object o : plainSelect.getJoins()) {
            if (o instanceof net.sf.jsqlparser.statement.select.Join join) {
                FromItem rightItem = join.getRightItem();
                if (rightItem instanceof Table table) {
                    if (table.getName().equalsIgnoreCase(hasDeptIdTableAlias)) {
                        deptTable = table;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 注册plainSelect
     */
    private void registerPlainSelect(boolean hasDeptTable) {
        if (!hasDeptTable) {
            if (null != hasDeptIdTableTable) {
                deptTable = hasDeptIdTableTable;
            } else {
                Join join = new Join();
                join.setInner(true);
                Table table = new Table(tableName);
                table.setAlias(new Alias(tableAlias, false));
                join.setRightItem(table);
                join.setOnExpressions(Collections.singletonList(
                        new EqualsTo(new Column(table, deptIdColumn), new Column(hasDeptIdTableTable, deptIdColumn))));
                plainSelect.getJoins().add(join);
                deptTable = table;
            }
        }
        registerDataScope();
    }



    /**
     * 查找表是否有部门ID
     */
    private void findTableHasDeptId() {
        if (findFromFrom()) {
            return;
        }
        findFromJoin();
    }

    private void findFromJoin() {
        List<Join> joins = plainSelect.getJoins();
        if (null == joins) {
            return;
        }
        for (Object o : joins) {
            if (o instanceof net.sf.jsqlparser.statement.select.Join join) {
                FromItem rightItem = join.getRightItem();
                if (rightItem instanceof Table table) {
                    String name = table.getName();
                    TableInfo tableInfo = getTableInfo(name);
                    if (null == tableInfo) {
                        continue;
                    }
                    if (findFromFrom(tableInfo, ObjectUtils.optional(table.getAlias(), Alias::getName, EMPTY), table)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * 查找表是否有部门ID
     *
     * @return
     */
    private boolean findFromFrom() {
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem == null) {
            return false;
        }

        if (fromItem instanceof Table table) {
            String name = table.getName();
            TableInfo tableInfo = getTableInfo(name);
            if (null == tableInfo) {
                return false;
            }
            return findFromFrom(tableInfo, ObjectUtils.optional(table.getAlias(), Alias::getName, EMPTY), table);
        }
        return false;
    }

    /**
     * 查找表是否有部门ID
     *
     * @param tableInfo 表信息
     * @return 是否有部门ID
     */
    private boolean findFromFrom(TableInfo tableInfo, String alias, Table table) {
        for (TableFieldInfo tableFieldInfo : tableInfo.getFieldList()) {
            if (tableFieldInfo.getColumn().equalsIgnoreCase(deptIdColumn)) {
                hasDeptIdTableAlias = alias;
                hasDeptIdTableTable = table;
                return true;
            }
        }
        return false;
    }
}
