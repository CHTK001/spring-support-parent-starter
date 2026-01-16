package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.chua.common.support.core.utils.ClassUtils;
import com.chua.starter.mybatis.annotations.DataScope;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.permission.TableDeptRegister;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Method;
import java.util.Map;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * 多数据权限处理器
 * <p>支持 @DataScope 注解进行声明式数据权限控制</p>
 * <p>功能特性：</p>
 * <ul>
 *   <li>支持方法级别和类级别的数据权限注解</li>
 *   <li>支持多表关联场景，通过deptAlias和userAlias指定表别名</li>
 *   <li>支持多种数据权限类型：ALL、SELF、DEPT、DEPT_AND_SUB、DEPT_SETS、CUSTOM</li>
 *   <li>使用性能优化的查询方式（LIKE前缀查询替代find_in_set）</li>
 * </ul>
 *
 * @author CH
 */
@Slf4j
public class MybatisPlusDataPermissionHandler implements MultiDataPermissionHandler {

    /**
     * 缓存 mappedStatementId 对应的 DataScope 注解
     */
    private static final Map<String, DataScopeInfo> DATA_SCOPE_CACHE = new ConcurrentReferenceHashMap<>(4096);

    private final MybatisPlusDataScopeProperties metaDataScopeProperties;

    public MybatisPlusDataPermissionHandler(MybatisPlusDataScopeProperties metaDataScopeProperties) {
        this.metaDataScopeProperties = metaDataScopeProperties;
    }

    /**
     * 获取SQL片段，用于数据权限过滤
     *
     * @param table            表对象
     * @param where            WHERE条件表达式
     * @param mappedStatementId MyBatis mappedStatementId
     * @return 过滤后的WHERE条件表达式，如果不需要过滤则返回null
     * @throws RuntimeException 数据权限处理失败时抛出异常
     */
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

            return dataScopeFilter(table, currentUser, metaDataScopeProperties, where, dataPermission, dataScopeInfo);
        } catch (Exception e) {
            log.error("数据权限处理异常, mappedStatementId: {}", mappedStatementId, e);
            // 异常时抛出异常，而不是静默返回空结果，避免掩盖问题
            throw new RuntimeException("数据权限处理失败: " + mappedStatementId, e);
        }
    }

    /**
     * 确定数据权限类型
     * <p>优先级：注解指定 > 用户当前权限</p>
     *
     * @param dataScopeInfo 注解信息，可能为null
     * @param currentUser   当前用户信息
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
     * <p>优先查找方法级别注解，如果未找到则查找类级别注解</p>
     * <p>结果会被缓存，避免重复解析</p>
     *
     * @param mappedStatementId MyBatis mappedStatementId，格式：com.example.mapper.UserMapper.selectList
     * @return 注解信息，如果未找到则返回null
     */
    private DataScopeInfo getDataScopeInfo(String mappedStatementId) {
        return DATA_SCOPE_CACHE.computeIfAbsent(mappedStatementId, this::parseDataScopeInfo);
    }

    /**
     * 解析 @DataScope 注解信息
     * <p>从mappedStatementId中解析类名和方法名，然后查找对应的注解</p>
     *
     * @param mappedStatementId MyBatis mappedStatementId，格式：com.example.mapper.UserMapper.selectList
     * @return 注解信息，如果未找到或解析失败则返回null
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

            // 使用ClassUtils替代Class.forName
            Class<?> mapperClass = ClassUtils.forName(className);

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

        } catch (Exception e) {
            log.debug("无法加载 Mapper 类或解析注解: {}", mappedStatementId, e);
        }
        return null;
    }

    /**
     * 查找方法上的 @DataScope 注解
     *
     * @param clazz       Mapper接口类
     * @param methodName  方法名
     * @return 注解，如果方法上未找到则返回null
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

    /**
     * 数据权限过滤
     *
     * @param table              表对象
     * @param currentUser        当前用户
     * @param dataScopeProperties 数据权限配置
     * @param where              WHERE条件
     * @param dataPermission     数据权限类型
     * @param dataScopeInfo      数据权限注解信息
     * @return 过滤后的WHERE条件表达式
     */
    private Expression dataScopeFilter(Table table,
                                       CurrentUser currentUser,
                                       MybatisPlusDataScopeProperties dataScopeProperties,
                                       Expression where,
                                       DataFilterTypeEnum dataPermission,
                                       DataScopeInfo dataScopeInfo) {
        if (null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return null;
        }

        String deptAlias = (dataScopeInfo != null && dataScopeInfo.deptAlias != null && !dataScopeInfo.deptAlias.isEmpty())
                ? dataScopeInfo.deptAlias : null;
        String userAlias = (dataScopeInfo != null && dataScopeInfo.userAlias != null && !dataScopeInfo.userAlias.isEmpty())
                ? dataScopeInfo.userAlias : null;

        return new TableDeptRegister(table, where, currentUser, dataPermission, dataScopeProperties, deptAlias, userAlias).register();
    }


    /**
     * @DataScope 注解信息封装
     * <p>用于缓存注解解析结果，避免重复解析</p>
     */
    private static class DataScopeInfo {
        /** 是否启用数据权限 */
        final boolean enabled;
        /** 是否使用用户当前的数据权限 */
        final boolean useUserPermission;
        /** 数据权限类型 */
        final DataFilterTypeEnum dataFilterType;
        /** 部门表别名，用于多表关联场景 */
        final String deptAlias;
        /** 用户表别名，用于多表关联场景 */
        final String userAlias;

        /**
         * 构造函数
         *
         * @param annotation @DataScope注解对象
         */
        DataScopeInfo(DataScope annotation) {
            this.enabled = annotation.enabled();
            this.useUserPermission = annotation.useUserPermission();
            this.dataFilterType = annotation.value();
            this.deptAlias = annotation.deptAlias();
            this.userAlias = annotation.userAlias();
        }
    }
}

