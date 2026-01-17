package com.chua.starter.mybatis.interceptor;

import com.chua.starter.datasource.util.ClassUtils;
import com.chua.starter.mybatis.annotations.DataScope;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.permission.DeptRegister;
import com.chua.starter.mybatis.permission.SelectDeptRegister;
import com.chua.starter.mybatis.permission.WhereDeptChecker;
import com.chua.starter.mybatis.permission.WhereDeptRegister;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 数据权限处理器
 * 处理查询语句的数据权限过滤
 *
 * @author CH
 */
@Slf4j
public class MybatisPlusPermissionHandler implements SelectDataPermissionHandler {

    private static final String CREATE_BY = "rule_create_by";
    private static final String DEPT_ID = "rule_dept_id";
    public static final NotEqualsTo NO_DATA = new NotEqualsTo();
    static {
        NO_DATA.setLeftExpression(new LongValue(1));
        NO_DATA.setRightExpression(new LongValue(1));
    }

    final MybatisPlusDataScopeProperties metaDataScopeProperties;

    /**
     * 构造函数
     *
     * @param metaDataScopeProperties 数据权限配置属性
     */
    public MybatisPlusPermissionHandler(MybatisPlusDataScopeProperties metaDataScopeProperties) {
        this.metaDataScopeProperties = metaDataScopeProperties;
    }


    /**
     * 处理查询语句，应用数据权限过滤
     *
     * @param plainSelect      查询对象
     * @param where           当前查询条件
     * @param mappedStatementId Mapper 方法全限定名
     * @param currentUser     当前登录用户
     */
    @Override
    public void processSelect(PlainSelect plainSelect, Expression where, String mappedStatementId, CurrentUser currentUser) {
        try {
            int lastDotIndex = mappedStatementId.lastIndexOf(".");
            if (lastDotIndex <= 0) {
                return;
            }

            String className = mappedStatementId.substring(0, lastDotIndex);
            String methodName = mappedStatementId.substring(lastDotIndex + 1);
            Class<?> clazz = ClassUtils.forName(className);
            if (clazz == null) {
                return;
            }

            List<Method> methods = Arrays.asList(clazz.getMethods());
            for (Method method : methods) {
                DataScope dataScope = method.getAnnotation(DataScope.class);
                if (dataScope != null) {
                    if (method.getName().equals(methodName) || (method.getName() + "_COUNT").equals(methodName)) {
                        DataFilterTypeEnum permission = dataScope.value() != null ? dataScope.value() : currentUser.getDataPermission();
                        dataScopeFilter(plainSelect, currentUser, metaDataScopeProperties, where, permission);
                    }
                    return;
                }
            }
            // 如果没有找到 DataScope 注解，使用用户默认权限
            dataScopeFilter(plainSelect, currentUser, metaDataScopeProperties, where, currentUser.getDataPermission());
        } catch (Exception e) {
            log.error("处理数据权限时发生异常: mappedStatementId={}", mappedStatementId, e);
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
