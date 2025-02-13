package com.chua.starter.mybatis.permission;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubSelect;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * where注入机构ID
 *
 * @author CH
 */
public class WhereDeptRegister implements DeptRegister {

    private final PlainSelect plainSelect;
    private final Expression where;
    private final CurrentUser user;
    private final DataFilterTypeEnum dataPermission;
    private final String tableName;
    private final String deptIdColumn;
    private final String deptTreeIdColumn;
    private final String createByColumn;
    private final Table currentTable;
    private String hasDeptIdTableAlias;

    public WhereDeptRegister(PlainSelect plainSelect,
                             Expression where,
                             CurrentUser user,
                             DataFilterTypeEnum dataPermission,
                             MybatisPlusDataScopeProperties scopeProperties,
                             Table currentTable) {
        this.plainSelect = plainSelect;
        this.where = where;
        this.user = user;
        this.dataPermission = dataPermission;
        this.tableName = scopeProperties.getTableName();
        this.deptIdColumn = scopeProperties.getDeptIdColumn();
        this.deptTreeIdColumn = scopeProperties.getDeptTreeIdColumn();
        this.createByColumn = scopeProperties.getCurrentUserIdColumn();
        this.currentTable = currentTable;
    }

    public Expression register() {
        if (DataFilterTypeEnum.DEPT_SETS == dataPermission) {
            if (StringUtils.isEmpty(user.getDataPermissionRule())) {
                plainSelect.setWhere(NO_DATA);
                return NO_DATA;
            }
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(createInExpression());
            plainSelect.setWhere(expression);
            return expression;
        }

        if (DataFilterTypeEnum.DEPT == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(currentTable, deptIdColumn));
            equalsTo.setRightExpression(new LongValue(user.getDeptId()));
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(equalsTo);
            plainSelect.setWhere(expression);
            return expression;
        }

        if (DataFilterTypeEnum.DEPT_AND_SUB == dataPermission) {
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(buildColumn(tableName, deptIdColumn));
            SubSelect subSelect = new SubSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectExpressionItem(new Column(deptIdColumn))));
            select.setFromItem(currentTable);
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
            return expression;
        }

        if (DataFilterTypeEnum.SELF == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(buildColumn(tableName, createByColumn));
            equalsTo.setRightExpression(new StringValue(user.getId()));
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(equalsTo);
            plainSelect.setWhere(expression);
            return expression;
        }

        plainSelect.setWhere(NO_DATA);
        return NO_DATA;
    }

    /**
     * 创建in表达式
     *
     * @return
     */
    private Expression createInExpression() {
        return new InExpression(
                new Column(currentTable, deptIdColumn),
                new ExpressionList(Arrays.stream(user.getDataPermissionRule().split(","))
                        .map(LongValue::new).collect(Collectors.toList()))
        );
    }

}
