package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.annotations.DataScope;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubSelect;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据处理器
 * @author CH
 */
public class MybatisPlusPermissionHandler implements SelectDataPermissionHandler {

    private static final String CREATE_BY = "rule_create_by";
    private static final String DEPT_ID = "rule_dept_id";
    @Resource
    private AuthService authService;

    @Override
    public Expression getSqlSegment(PlainSelect plainSelect, Expression where, String mappedStatementId) {
        try {
            Class<?> clazz = Class.forName(mappedStatementId.substring(0, mappedStatementId.lastIndexOf(".")));
            String methodName = mappedStatementId.substring(mappedStatementId.lastIndexOf(".") + 1);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                DataScope annotation = method.getAnnotation(DataScope.class);
                if (ObjectUtils.isNotEmpty(annotation) && (method.getName().equals(methodName) || (method.getName() + "_COUNT").equals(methodName))) {
                    // 获取当前的用户
                    CurrentUser currentUser = SpringBeanUtils.getBean(AuthService.class).getCurrentUser();
                    if (ObjectUtils.isNotEmpty(currentUser) && ObjectUtils.isNotEmpty(currentUser) && !currentUser.isAdmin()) {
                        return dataScopeFilter(currentUser, annotation.value(), where);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return where;

    }

    /**
     * 构建过滤条件
     *
     * @param user 当前登录用户
     * @param where 当前查询条件
     * @return 构建后查询条件
     */
    public static Expression dataScopeFilter(CurrentUser user, String tableAlias, Expression where) {
        Set<String> roles = user.getRoles();
        if(isAdmin(roles)) {
            return where;
        }
        DataFilterTypeEnum dataPermission = user.getDataPermission();
        if(null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return where;
        }

        if (DataFilterTypeEnum.DEPT_SETS == dataPermission) {
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(buildColumn(tableAlias, "dept_id"));
            SubSelect subSelect = new SubSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectExpressionItem(new Column("dept_id"))));
            select.setFromItem(new Table("sys_dept"));
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column("dept_id"));
            Function function = new Function();
            function.setName("IN");
            function.setParameters(new ExpressionList(Arrays.stream(user.getDeptIds().split(","))
                    .map(LongValue::new).collect(Collectors.toList())));
            equalsTo.setRightExpression(function);
            select.setWhere(equalsTo);
            subSelect.setSelectBody(select);
            inExpression.setRightExpression(subSelect);
            return inExpression;
        }
        if (DataFilterTypeEnum.DEPT == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(buildColumn(tableAlias, "dept_id"));
            equalsTo.setRightExpression(new LongValue(user.getDeptId()));
            return equalsTo;
        }

        if (DataFilterTypeEnum.DEPT_AND_SUB == dataPermission) {
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(buildColumn(tableAlias, "dept_id"));
            SubSelect subSelect = new SubSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectExpressionItem(new Column("dept_id"))));
            select.setFromItem(new Table("sys_dept"));
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(new Column("dept_id"));
            equalsTo.setRightExpression(new LongValue(user.getDeptId()));
            Function function = new Function();
            function.setName("find_in_set");
            function.setParameters(new ExpressionList(new LongValue(user.getDeptId()) , new Column("dept_id")));
            select.setWhere(new OrExpression(equalsTo, function));
            subSelect.setSelectBody(select);
            inExpression.setRightExpression(subSelect);
            return inExpression;
        }
        
        if (DataFilterTypeEnum.SELF == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(buildColumn(tableAlias, "create_by"));
            equalsTo.setRightExpression(new StringValue(user.getId()));
            return equalsTo;
        }

        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new StringValue("1"));
        equalsTo.setRightExpression(new LongValue("2"));
        return equalsTo;
    }

    /**
     * 是管理员
     *
     * @param roles 角色
     * @return boolean
     */
    private static boolean isAdmin(Set<String> roles) {
        if(roles.contains("ADMIN")) {
            return true;
        }
        
        return false;
    }

    /**
     * 构建Column
     *
     * @param tableAlias 表别名
     * @param columnName 字段名称
     * @return 带表别名字段
     */
    public static Column buildColumn(String tableAlias, String columnName) {
        if (StringUtils.isNotEmpty(tableAlias)) {
            columnName = tableAlias + "." + columnName;
        }
        return new Column(columnName);
    }


}
