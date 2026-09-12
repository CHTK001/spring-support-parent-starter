package com.chua.starter.mybatis.interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.mybatis.annotations.DataScope;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.permission.TableDeptRegister;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.Collection;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

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
* @since 4.0.0
 */
public class MybatisPlusDataPermissionHandler implements MultiDataPermissionHandler {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MybatisPlusDataPermissionHandler.class);
    /** 父_admin */
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    /** Admin */
    private static final String ADMIN = "ADMIN";
    /** Dept_leader */
    private static final String DEPT_LEADER = "DEPT_LEADER";
    /** Managed_dept_树_标识 */
    private static final String MANAGED_DEPT_TREE_IDS = "managedDeptTreeIds";

    /**
    * 缓存 mapped对账单标识 对应的 数据scope 注解
     */
    private static final Map<String, DataScopeInfo> DATA_SCOPE_CACHE = new ConcurrentReferenceHashMap<>(4096);

    /** Meta数据作用域属性 */
    private final MybatisPlusDataScopeProperties metaDataScopeProperties;
    /** 认证服务供应商 */
    private final Supplier<AuthService> authServiceSupplier;

    /**
    * 创建 mybatisplus数据权限处理器 实例
    * @param metaDataScopeProperties meta数据scope属性
     */
    public MybatisPlusDataPermissionHandler(MybatisPlusDataScopeProperties metaDataScopeProperties) {
        this(metaDataScopeProperties, () -> null);
    }

    /**
    * 创建 mybatisplus数据权限处理器 实例
    * @param metaDataScopeProperties meta数据scope属性
    * @param authServiceSupplier 认证服务供应商
     */
    public MybatisPlusDataPermissionHandler(MybatisPlusDataScopeProperties metaDataScopeProperties,
                                            Supplier<AuthService> authServiceSupplier) {
        this.metaDataScopeProperties = metaDataScopeProperties;
        this.authServiceSupplier = authServiceSupplier;
    }

    /**
    * 获取SQL片段，用于数据权限过滤
    *
    * @param table            表对象
    * @param where            WHERE条件表达式
    * @param mappedStatementId MyBatis mapped对账单标识
    * @return 过滤后的WHERE条件表达式，如果不需要过滤则返回null
    * @throws RuntimeException 数据权限处理失败时抛出异常
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!metaDataScopeProperties.isEnable()) {
            log.debug("[数据权限] 未启用 enable=false, mappedStatementId={}, table={}", mappedStatementId, table.getName());
            return null;
        }

        try {
 // 获取 @数据scope 注解信息
            DataScopeInfo dataScopeInfo = getDataScopeInfo(mappedStatementId);

            // 检查注解是否禁用数据权限
            if (dataScopeInfo != null && !dataScopeInfo.enabled) {
                return null;
            }

            // 获取当前用户
            CurrentUser currentUser = getCurrentUser();
            if (currentUser == null) {
                log.debug("[数据权限] 当前用户为空, mappedStatementId={}, table={}", mappedStatementId, table.getName());
                return null;
            }

            // 确定数据权限类型
            DataFilterTypeEnum dataPermission = determineDataPermission(dataScopeInfo, currentUser);

            log.debug("[数据权限] mappedStatementId={}, table={}, dataPermission={}, dataPermissionRule={}, roles={}",
                    mappedStatementId, table.getName(), dataPermission, currentUser.getDataPermissionRule(), currentUser.getRoles());

            return dataScopeFilter(table, currentUser, metaDataScopeProperties, where, dataPermission, dataScopeInfo);
        } catch (Exception e) {
            log.error("数据权限处理异常, mappedStatementId: {}", mappedStatementId, e);
            // 异常时抛出异常，而不是静默返回空结果，避免掩盖问题
            /**
            * runtime异常。
            * @param mappedStatementId mapped对账单标识
            * @param e e
            * @return runtime异常的结果
             */
            throw new RuntimeException("数据权限处理失败: " + mappedStatementId, e);
        }
    }

    /**
    * 确定数据权限类型
    * <p>优先级：注解指定 > 用户当前权限</p>
    *
    * @param dataScopeInfo 注解信息，可能为空
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
    * 获取 @数据scope 注解信息
    * <p>优先查找方法级别注解，如果未找到则查找类级别注解</p>
    * <p>结果会被缓存，避免重复解析</p>
    *
    * @param mappedStatementId MyBatis mapped对账单标识，格式：com.example.映射器.用户映射器.选择列表
    * @return 注解信息，如果未找到则返回null
     */
    private DataScopeInfo getDataScopeInfo(String mappedStatementId) {
        return DATA_SCOPE_CACHE.computeIfAbsent(mappedStatementId, this::parseDataScopeInfo);
    }

    /**
    * 解析 @数据scope 注解信息
    * <p>从mappedStatementId中解析类名和方法名，然后查找对应的注解</p>
    *
    * @param mappedStatementId MyBatis mapped对账单标识，格式：com.example.映射器.用户映射器.选择列表
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
            String methodName = normalizeMethodName(mappedStatementId.substring(lastDot + 1));

 // 使用类工具替代类.for名称
            Class<?> mapperClass = ClassUtils.forName(className);

            // 先查找方法级别的注解
            DataScope methodAnnotation = findMethodAnnotation(mapperClass, methodName);
            if (methodAnnotation != null) {
                /**
                * 数据scope信息。
                * @param methodAnnotation 方法注解
                * @return 数据scope信息的结果
                 */
                return new DataScopeInfo(methodAnnotation);
            }

            // 再查找类级别的注解
            DataScope classAnnotation = mapperClass.getAnnotation(DataScope.class);
            if (classAnnotation != null) {
                /**
                * 数据scope信息。
                * @param classAnnotation 类注解
                * @return 数据scope信息的结果
                 */
                return new DataScopeInfo(classAnnotation);
            }

        } catch (Exception e) {
            log.debug("无法加载 Mapper 类或解析注解: {}", mappedStatementId, e);
        }
        return null;
    }

    /**
    * 查找方法上的 @数据scope 注解
    *
    * @param clazz       映射器接口类
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
    * normalize方法名称
    *
    * @param methodName 方法名称
    * @return normalize方法名称的结果
     */
    private String normalizeMethodName(String methodName) {
        if (null == methodName || methodName.isBlank()) {
            return methodName;
        }
        if (methodName.endsWith("_mpCount")) {
            return methodName.substring(0, methodName.length() - 8);
        }
        if (methodName.endsWith("_COUNT") || methodName.endsWith("_count")) {
            return methodName.substring(0, methodName.length() - 6);
        }
        return methodName;
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
        if (currentUser.hasRole(SUPER_ADMIN) || currentUser.hasRole(ADMIN)) {
            return null;
        }

        String deptAlias = (dataScopeInfo != null && dataScopeInfo.deptAlias != null && !dataScopeInfo.deptAlias.isEmpty())
                ? dataScopeInfo.deptAlias : null;
        String userAlias = (dataScopeInfo != null && dataScopeInfo.userAlias != null && !dataScopeInfo.userAlias.isEmpty())
                ? dataScopeInfo.userAlias : null;

        if (hasManagedDeptLeaderScope(currentUser)) {
            /**
            * tabledept注册。
            * @param table table
            * @param null 空
            * @param currentUser 当前用户
            * @param dataPermission 数据权限
            * @param dataScopeProperties 数据scope属性
            * @param deptAlias dept别名
            * @param userAlias 用户别名
            * @return tabledept注册的结果
             */
            return new TableDeptRegister(table, null, currentUser, dataPermission, dataScopeProperties, deptAlias, userAlias).register();
        }

        if (null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return null;
        }

        /**
        * tabledept注册。
        * @param table table
        * @param null 空
        * @param currentUser 当前用户
        * @param dataPermission 数据权限
        * @param dataScopeProperties 数据scope属性
        * @param deptAlias dept别名
        * @param userAlias 用户别名
        * @return tabledept注册的结果
         */
        return new TableDeptRegister(table, null, currentUser, dataPermission, dataScopeProperties, deptAlias, userAlias).register();
    }

    /**
    * 是否拥有manageddept铅scope
    *
    * @param currentUser 当前用户
    * @return 是否包含manageddept铅scope的结果
     */
    private boolean hasManagedDeptLeaderScope(CurrentUser currentUser) {
        if (!currentUser.hasRole(DEPT_LEADER) || null == currentUser.getExt()) {
            return false;
        }
        Object managedDeptTreeIds = currentUser.getExt().get(MANAGED_DEPT_TREE_IDS);
        if (managedDeptTreeIds instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return null != managedDeptTreeIds && !managedDeptTreeIds.toString().isBlank();
    }

    /**
    * 获取当前用户
    *
    * @return 获取当前用户的结果
     */
    private CurrentUser getCurrentUser() {
        if (authServiceSupplier == null) {
            return null;
        }
        AuthService authService = authServiceSupplier.get();
        return authService == null ? null : authService.getCurrentUser();
    }


    /**
    * @数据scope 注解信息封装
    * <p>用于缓存注解解析结果，避免重复解析</p>
    * @author CH
    * @since 4.0.0
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
        * @param annotation @数据scope注解对象
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

