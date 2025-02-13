package com.chua.starter.mybatis.permission;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * select注入机构ID
 *
 * @author CH
 */
public class SelectDeptRegister implements DeptRegister {

    private final PlainSelect plainSelect;
    private final Expression where;
    private final CurrentUser user;
    private final DataFilterTypeEnum dataPermission;
    private final String tableName;
    private final String deptIdColumn;
    private final String deptTreeIdColumn;
    private final String createByColumn;
    private String hasDeptIdTableAlias;
    private Table hasDeptIdTableTable;
    private Table deptTable;

    public SelectDeptRegister(PlainSelect plainSelect,
                              Expression where,
                              CurrentUser user,
                              DataFilterTypeEnum dataPermission,
                              MybatisPlusDataScopeProperties scopeProperties) {
        this.plainSelect = plainSelect;
        this.where = where;
        this.user = user;
        this.dataPermission = dataPermission;
        this.tableName = scopeProperties.getTableName();
        this.deptIdColumn = scopeProperties.getDeptIdColumn();
        this.deptTreeIdColumn = scopeProperties.getDeptTreeIdColumn();
        this.createByColumn = scopeProperties.getCurrentUserIdColumn();
    }

    @Override
    public Expression register() {
        findTableHasDeptId();
        if (null == hasDeptIdTableAlias) {
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
            Join join = new Join();
            join.setInner(true);
            Table table = new Table(tableName);
            table.setAlias(new Alias(tableName, false));
            join.setRightItem(table);
            join.setOnExpressions(Collections.singletonList(
                    new EqualsTo(new Column(table, deptIdColumn), new Column(hasDeptIdTableTable, deptIdColumn))));
            plainSelect.getJoins().add(join);
            deptTable = table;
        }
        registerDataScope();
    }

    private void registerDataScope() {
        if (DataFilterTypeEnum.DEPT_SETS == dataPermission) {
            if (StringUtils.isEmpty(user.getDataPermissionRule())) {
                plainSelect.setWhere(NO_DATA);
                return;
            }
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(createInExpression());
            plainSelect.setWhere(expression);
            return;
        }

        if (DataFilterTypeEnum.DEPT == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(deptTable, deptIdColumn));
            equalsTo.setRightExpression(new LongValue(user.getDeptId()));
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(equalsTo);
            plainSelect.setWhere(expression);
            return;
        }

        if (DataFilterTypeEnum.DEPT_AND_SUB == dataPermission) {
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(buildColumn(tableName, deptIdColumn));
            SubSelect subSelect = new SubSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectExpressionItem(new Column(deptIdColumn))));
            select.setFromItem(deptTable);
            Function function = new Function();
            function.setName("find_in_set");
            function.setParameters(new ExpressionList(new LongValue(user.getDeptId()), new Column(deptTreeIdColumn)));
            select.setWhere(function);
            subSelect.setSelectBody(select);
            inExpression.setRightExpression(subSelect);
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(inExpression);
            plainSelect.setWhere(expression);
            return;
        }

        if (DataFilterTypeEnum.SELF == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(buildColumn(tableName, createByColumn));
            equalsTo.setRightExpression(new StringValue(user.getId()));
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(equalsTo);
            plainSelect.setWhere(expression);
            return;
        }

        plainSelect.setWhere(NO_DATA);
    }

    /**
     * 创建in表达式
     *
     * @return
     */
    private Expression createInExpression() {
        return new InExpression(
                new Column(deptTable, deptIdColumn),
                new ExpressionList(Arrays.stream(user.getDataPermissionRule().split(","))
                        .map(LongValue::new).collect(Collectors.toList()))
        );
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
        for (Object o : plainSelect.getJoins()) {
            if (o instanceof net.sf.jsqlparser.statement.select.Join join) {
                FromItem rightItem = join.getRightItem();
                if (rightItem instanceof Table table) {
                    String name = table.getName();
                    TableInfo tableInfo = getTableInfo(name);
                    if (null == tableInfo) {
                        continue;
                    }
                    if (findFromFrom(tableInfo, table.getAlias().getName(), table)) {
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
            return findFromFrom(tableInfo, table.getAlias().getName(), table);
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
