package com.chua.starter.mybatis.interceptor;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.mybatis.annotations.DataScope;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
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

import java.lang.reflect.Method;
import java.util.List;

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
    static {
        NO_DATA.setLeftExpression(new LongValue(1));
        NO_DATA.setRightExpression(new LongValue(1));
    }

    final MybatisPlusDataScopeProperties metaDataScopeProperties;


    @Override
    public void processSelect(PlainSelect plainSelect, Expression where, String mappedStatementId, CurrentUser currentUser) {
        try {
            Class<?> clazz = Class.forName(mappedStatementId.substring(0, mappedStatementId.lastIndexOf(".")));
            String methodName = mappedStatementId.substring(mappedStatementId.lastIndexOf(".") + 1);
            List<Method> methods = ClassUtils.getMethods(clazz);
            for (Method method : methods) {
                DataScope dataScope = method.getAnnotation(DataScope.class);
                if (ObjectUtils.isNotEmpty(dataScope)) {
                    if ((method.getName().equals(methodName) || (method.getName() + "_COUNT").equals(methodName))) {
                        // 获取当前的用户
                        dataScopeFilter(plainSelect, currentUser, metaDataScopeProperties, where, ObjectUtils.defaultIfNull(dataScope.value(), currentUser.getDataPermission()));
                    }
                    return;
                }
                dataScopeFilter(plainSelect, currentUser, metaDataScopeProperties, where, currentUser.getDataPermission());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建过滤条件
     *
     * @param plainSelect       查询对象
     * @param user              当前登录用户
     * @param where             当前查询条件
     * @param dataPermission    数据权限
     */
    public void dataScopeFilter(PlainSelect plainSelect,
                                CurrentUser user,
                                MybatisPlusDataScopeProperties dataScopeProperties,
                                Expression where,
                                DataFilterTypeEnum dataPermission) {
        if (null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return;
        }

        String tableAlias = dataScopeProperties.getTableName();
        String columnName = dataScopeProperties.getDeptIdColumn();
        WhereDeptChecker whereDeptChecker = new WhereDeptChecker(where, tableAlias, columnName);
        boolean whereHasDeptId = whereDeptChecker.check();
        DeptRegister register = whereHasDeptId ? new WhereDeptRegister(plainSelect, where, user, dataPermission, dataScopeProperties, whereDeptChecker.getCurrentTable())
                : new SelectDeptRegister(plainSelect, where, user, dataPermission, dataScopeProperties);

        register.register();
    }

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        return where;
    }
}
