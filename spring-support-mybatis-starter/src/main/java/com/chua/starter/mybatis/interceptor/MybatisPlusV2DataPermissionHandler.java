package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.chua.starter.common.support.annotations.DataScope;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.permission.TableDeptV2Register;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Method;
import java.util.Map;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * 多数据权限处理器
 * <p>支持 @DataScope 注解进行声明式数据权限控制</p>
 *
 * @author CH
 */
@Slf4j
public class MybatisPlusV2DataPermissionHandler implements MultiDataPermissionHandler {

    /**
     * 缓存 mappedStatementId 对应的 DataScope 注解
     */
    private static final Map<String, DataScopeInfo> DATA_SCOPE_CACHE = new ConcurrentReferenceHashMap<>(4096);

    private final MybatisPlusDataScopeProperties metaDataScopeProperties;

    public MybatisPlusV2DataPermissionHandler(MybatisPlusDataScopeProperties metaDataScopeProperties) {
        this.metaDataScopeProperties = metaDataScopeProperties;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!metaDataScopeProperties.isEnable()) {
            return null;
        }

        try {
            // 获取 @DataScope 注解信息
            DataScopeInfo dataScopeInfo = getDataScopeInfo(mappedStatementId);

            // 检查注解是否禁用数据权限
            if (dataScopeInfo != null && !dataScopeInfo.enabled) {
                return null;
            }

            // 获取当前用户
            AuthService authService = SpringBeanUtils.getBean(AuthService.class);
            if (authService == null) {
                return null;
            }

            CurrentUser currentUser = authService.getCurrentUser();
            if (currentUser == null) {
                return null;
            }

            // 确定数据权限类型
            DataFilterTypeEnum dataPermission = determineDataPermission(dataScopeInfo, currentUser);

            return dataScopeFilter(table, currentUser, metaDataScopeProperties, where, dataPermission);
        } catch (Exception e) {
            log.error("数据权限处理异常, mappedStatementId: {}", mappedStatementId, e);
        }
        return createNoData(where);
    }

    /**
     * 确定数据权限类型
     *
     * @param dataScopeInfo 注解信息
     * @param currentUser   当前用户
     * @return 数据权限类型
     */
    private DataFilterTypeEnum determineDataPermission(DataScopeInfo dataScopeInfo, CurrentUser currentUser) {
        // 如果没有注解或者注解设置为使用用户权限，则使用用户当前的数据权限
        if (dataScopeInfo == null || dataScopeInfo.useUserPermission) {
            return currentUser.getDataPermission();
        }
        // 否则使用注解指定的数据权限类型
        return dataScopeInfo.dataFilterType;
    }

    /**
     * 获取 @DataScope 注解信息
     *
     * @param mappedStatementId MyBatis mappedStatementId
     * @return 注解信息，可能为 null
     */
    private DataScopeInfo getDataScopeInfo(String mappedStatementId) {
        return DATA_SCOPE_CACHE.computeIfAbsent(mappedStatementId, this::parseDataScopeInfo);
    }

    /**
     * 解析 @DataScope 注解信息
     *
     * @param mappedStatementId MyBatis mappedStatementId
     * @return 注解信息
     */
    private DataScopeInfo parseDataScopeInfo(String mappedStatementId) {
        try {
            // 解析类名和方法名
            int lastDot = mappedStatementId.lastIndexOf('.');
            if (lastDot <= 0) {
                return null;
            }

            String className = mappedStatementId.substring(0, lastDot);
            String methodName = mappedStatementId.substring(lastDot + 1);

            Class<?> mapperClass = Class.forName(className);

            // 先查找方法级别的注解
            DataScope methodAnnotation = findMethodAnnotation(mapperClass, methodName);
            if (methodAnnotation != null) {
                return new DataScopeInfo(methodAnnotation);
            }

            // 再查找类级别的注解
            DataScope classAnnotation = mapperClass.getAnnotation(DataScope.class);
            if (classAnnotation != null) {
                return new DataScopeInfo(classAnnotation);
            }

        } catch (ClassNotFoundException e) {
            log.debug("无法加载 Mapper 类: {}", mappedStatementId);
        } catch (Exception e) {
            log.warn("解析 @DataScope 注解异常: {}", mappedStatementId, e);
        }
        return null;
    }

    /**
     * 查找方法上的 @DataScope 注解
     *
     * @param clazz      类
     * @param methodName 方法名
     * @return 注解，可能为 null
     */
    private DataScope findMethodAnnotation(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                DataScope annotation = method.getAnnotation(DataScope.class);
                if (annotation != null) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private Expression dataScopeFilter(Table table,
                                       CurrentUser currentUser,
                                       MybatisPlusDataScopeProperties dataScopeProperties,
                                       Expression where,
                                       DataFilterTypeEnum dataPermission) {
        if (null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return null;
        }

        return new TableDeptV2Register(table, where, currentUser, dataPermission, dataScopeProperties).register();
    }

    /**
     * 创建没有数据的条件
     *
     * @param where where
     * @return 条件
     */
    private Expression createNoData(Expression where) {
        if (null == where) {
            return NO_DATA;
        }
        return new AndExpression(where, NO_DATA);
    }

    /**
     * @DataScope 注解信息封装
     */
    private static class DataScopeInfo {
        final boolean enabled;
        final boolean useUserPermission;
        final DataFilterTypeEnum dataFilterType;
        final String deptAlias;
        final String userAlias;

        DataScopeInfo(DataScope annotation) {
            this.enabled = annotation.enabled();
            this.useUserPermission = annotation.useUserPermission();
            this.dataFilterType = annotation.value();
            this.deptAlias = annotation.deptAlias();
            this.userAlias = annotation.userAlias();
        }
    }
}
