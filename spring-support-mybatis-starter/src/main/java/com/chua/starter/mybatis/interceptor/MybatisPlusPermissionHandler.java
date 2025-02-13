package com.chua.starter.mybatis.interceptor;

import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.annotations.DataScope;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.permission.DeptRegister;
import com.chua.starter.mybatis.permission.SelectDeptRegister;
import com.chua.starter.mybatis.permission.WhereDeptChecker;
import com.chua.starter.mybatis.permission.WhereDeptRegister;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * 数据处理器
 *
 * @author CH
 */
@RequiredArgsConstructor
public class MybatisPlusPermissionHandler implements SelectDataPermissionHandler {

    private static final String CREATE_BY = "rule_create_by";
    private static final String DEPT_ID = "rule_dept_id";
    public static final NotEqualsTo NO_DATA = new NotEqualsTo();
    static final Map<String, Expression> MAPPED_STATEMENT_ID = new ConcurrentReferenceHashMap<>(4096);

    static {
        NO_DATA.setLeftExpression(new LongValue(1));
        NO_DATA.setRightExpression(new LongValue(1));
    }

    final AuthService authService;
    final MybatisPlusDataScopeProperties metaDataScopeProperties;

    /**
     * 构建过滤条件
     *
     * @param plainSelect
     * @param user              当前登录用户
     * @param where             当前查询条件
     * @param mappedStatementId
     * @return 构建后查询条件
     */
    public static Expression dataScopeFilter(PlainSelect plainSelect, CurrentUser user, MybatisPlusDataScopeProperties dataScopeProperties, Expression where, String mappedStatementId) {
        Set<String> roles = user.getRoles();

        if (isSuperAdmin(user)) {
            return where;
        }

        if (isAdmin(roles)) {
            return where;
        }

        DataFilterTypeEnum dataPermission = user.getDataPermission();
        if (null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return where;
        }

        //  return MAPPED_STATEMENT_ID.computeIfAbsent(mappedStatementId, k -> {
        String tableAlias = dataScopeProperties.getTableName();
        String columnName = dataScopeProperties.getDeptIdColumn();
        WhereDeptChecker whereDeptChecker = new WhereDeptChecker(where, tableAlias, columnName);
        boolean whereHasDeptId = whereDeptChecker.check();
        DeptRegister register = whereHasDeptId ? new WhereDeptRegister(plainSelect, where, user, dataPermission, dataScopeProperties, whereDeptChecker.getCurrentTable())
                : new SelectDeptRegister(plainSelect, where, user, dataPermission, dataScopeProperties);

        return register.register();
    }

    /**
     * 是否超级管理员
     *
     * @param user 用户信息
     * @return boolean
     */
    private static boolean isSuperAdmin(CurrentUser user) {
        return "sa".equals(user.getUsername());
    }

    @Override
    public Expression processSelect(PlainSelect plainSelect, Expression where, String mappedStatementId) {
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
                        return dataScopeFilter(plainSelect, currentUser, metaDataScopeProperties, where, mappedStatementId);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return where;

    }

    /**
     * 是管理员
     *
     * @param roles 角色
     * @return boolean
     */
    private static boolean isAdmin(Set<String> roles) {
        return roles.contains("ADMIN");
    }



}
