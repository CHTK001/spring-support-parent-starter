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
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * 注入机构ID
 *
 * @author CH
 */
public abstract class AbstractDeptRegister implements DeptRegister {
    protected final PlainSelect plainSelect;
    protected final Expression where;
    protected final CurrentUser user;
    protected final DataFilterTypeEnum dataPermission;
    protected final String tableName;
    protected final String deptIdColumn;
    protected final String deptTreeIdColumn;
    protected final String createByColumn;
    protected final String tableAlias;
    protected String hasDeptIdTableAlias;
    protected Table hasDeptIdTableTable;
    protected Table deptTable;

    public AbstractDeptRegister(PlainSelect plainSelect,
                                Expression where,
                                CurrentUser user,
                                DataFilterTypeEnum dataPermission,
                                MybatisPlusDataScopeProperties scopeProperties) {
        this.plainSelect = plainSelect;
        this.where = where;
        this.user = user;
        this.dataPermission = dataPermission;
        this.tableName = scopeProperties.getTableName();
        this.tableAlias = scopeProperties.getTableName() + "_" + System.currentTimeMillis();
        this.deptIdColumn = scopeProperties.getDeptIdColumn();
        this.deptTreeIdColumn = scopeProperties.getDeptTreeIdColumn();
        this.createByColumn = scopeProperties.getCurrentUserIdColumn();
    }

    protected Expression registerDataScope() {
        if (DataFilterTypeEnum.DEPT_SETS == dataPermission) {
            if (StringUtils.isEmpty(user.getDataPermissionRule())) {
                plainSelect.setWhere(NO_DATA);
                return NO_DATA;
            }

            Expression inExpression = createInExpression();
            if (null == where) {
                plainSelect.setWhere(inExpression);
                return inExpression;
            }
            AndExpression expression = new AndExpression();
            expression.setLeftExpression(where);
            expression.setRightExpression(inExpression);
            plainSelect.setWhere(expression);
            return expression;
        }

        if (DataFilterTypeEnum.DEPT == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(deptTable, deptIdColumn));
            equalsTo.setRightExpression(new LongValue(user.getDeptId()));
            if (null == where) {
                plainSelect.setWhere(equalsTo);
                return equalsTo;
            }
            AndExpression expression = new AndExpression();
            expression.setRightExpression(equalsTo);
            expression.setLeftExpression(where);
            plainSelect.setWhere(expression);
            return expression;
        }

        if (DataFilterTypeEnum.DEPT_AND_SUB == dataPermission) {
            InExpression inExpression = new InExpression();
            // 设置左侧表达式 - 列名
            inExpression.setLeftExpression(new Column(deptTable, deptIdColumn));

            // 创建子查询
            ParenthesedSelect subSelect = new ParenthesedSelect();
            PlainSelect select = new PlainSelect();

            // 设置 SELECT 部分
            select.addSelectItems(new SelectItem<>(new Column(deptIdColumn)));

            // 设置 FROM 部分
            select.setFromItem(deptTable);

            // 创建函数表达式 - find_in_set
            Function function = new Function();
            function.setName("find_in_set");
            ExpressionList params = new ExpressionList<>();
            params.addExpressions(new LongValue(user.getDeptId()), new Column(deptTreeIdColumn));
            function.setParameters(params);

            // 设置 WHERE 条件
            select.setWhere(function);

            // 将子查询设置为 IN 表达式的右侧
            subSelect.setSelect(select);
            inExpression.setRightExpression(subSelect);

            if (where == null) {
                plainSelect.setWhere(inExpression);
                return inExpression;
            }

            // 创建 AND 表达式
            AndExpression expression = new AndExpression(where, inExpression);
            plainSelect.setWhere(expression);
            return expression;
        }

        if (DataFilterTypeEnum.SELF == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column(deptTable, createByColumn));
            equalsTo.setRightExpression(new StringValue(user.getId()));
            if (null == where) {
                plainSelect.setWhere(equalsTo);
                return equalsTo;
            }
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
                new Column(deptTable, deptIdColumn),
                new ExpressionList(Arrays.stream(user.getDataPermissionRule().split(","))
                        .map(LongValue::new).collect(Collectors.toList()))
        );
    }
}
