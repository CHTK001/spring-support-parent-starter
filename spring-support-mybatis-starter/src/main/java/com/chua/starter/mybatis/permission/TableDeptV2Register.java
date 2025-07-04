package com.chua.starter.mybatis.permission;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * 注入机构ID
 *
 * @author CH
 */
public class TableDeptV2Register implements DeptRegister {

    private final Table table;
    private final Expression where;
    private final CurrentUser currentUser;
    private final DataFilterTypeEnum dataPermission;
    private final MybatisPlusDataScopeProperties dataScopeProperties;
    private final String deptIdColumn;
    private final String createByColumn;
    private final String deptTreeIdColumn;

    public TableDeptV2Register(Table table, Expression where, CurrentUser currentUser, DataFilterTypeEnum dataPermission, MybatisPlusDataScopeProperties dataScopeProperties) {
        this.table = table;
        this.where = where;
        this.currentUser = currentUser;
        this.dataPermission = dataPermission;
        this.dataScopeProperties = dataScopeProperties;
        deptIdColumn = dataScopeProperties.getDeptIdColumn();
        createByColumn = dataScopeProperties.getCurrentUserIdColumn();
        deptTreeIdColumn = dataScopeProperties.getDeptTreeIdColumn();
    }

    @Override
    public Expression register() {
        TableInfo tableInfo = getTableInfo(table.getName());
        if (null == tableInfo) {
            return null;
        }
        return createExpression(tableInfo);
    }

    /**
     * 创建表达式
     *
     * @param tableInfo 表信息
     * @return 表达式
     */
    private Expression createExpression(TableInfo tableInfo) {
        boolean deptIdFromTable = findDeptIdFromTable(tableInfo);
        if (!deptIdFromTable) {
            return null;
        }

        return createDeptWhere();
    }

    /**
     * 创建部门表达式
     *
     * @return 表达式
     */
    private Expression createDeptWhere() {
        if (DataFilterTypeEnum.DEPT_SETS == dataPermission) {
            if (StringUtils.isEmpty(currentUser.getDataPermissionRule())) {
                return NO_DATA;
            }

            Expression inExpression = createInExpression();
            if (null == where) {
                return NO_DATA;
            }
            return inExpression;
        }

        if (DataFilterTypeEnum.DEPT == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(table, deptIdColumn));
            equalsTo.setRightExpression(new LongValue(currentUser.getDeptId()));
            if (null == where) {
                return NO_DATA;
            }
            return equalsTo;
        }

        if (DataFilterTypeEnum.DEPT_AND_SUB == dataPermission) {
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(new Column(table, deptIdColumn));
            ParenthesedSelect subSelect = new ParenthesedSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectItem<>(new Column(deptIdColumn))));
            select.setFromItem(table);
            Function function = new Function();
            function.setName("find_in_set");
            function.setParameters(new ExpressionList<>(new LongValue(currentUser.getDeptId()), new Column(deptTreeIdColumn)));
            select.setWhere(function);
            subSelect.setSelect(select);
            inExpression.setRightExpression(subSelect);
            if (null == where) {
                return NO_DATA;
            }
            return inExpression;
        }

        if (DataFilterTypeEnum.SELF == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(table, createByColumn));
            equalsTo.setRightExpression(new StringValue(currentUser.getUserId()));
            if (null == where) {
                return NO_DATA;
            }
            return equalsTo;
        }

        return NO_DATA;
    }

    /**
     * 查找表是否有部门ID
     *
     * @param tableInfo 表信息
     * @return 是否有部门ID
     */
    private boolean findDeptIdFromTable(TableInfo tableInfo) {
        for (TableFieldInfo tableFieldInfo : tableInfo.getFieldList()) {
            if (tableFieldInfo.getColumn().equalsIgnoreCase(deptIdColumn)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 创建in表达式
     *
     * @return
     */
    private Expression createInExpression() {
        return new InExpression(
                new Column(table, deptIdColumn),
                new ExpressionList<>(Arrays.stream(currentUser.getDataPermissionRule().split(","))
                        .map(LongValue::new).collect(Collectors.toList()))
        );
    }
}
