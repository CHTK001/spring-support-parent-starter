package com.chua.starter.mybatis.interceptor;

import com.chua.common.support.core.utils.ClassUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.mybatis.annotations.DeleteOnlySelfOnMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 删除只能删除自己拦截器
 * 用于在执行删除操作时，自动添加创建人条件，确保用户只能删除自己创建的数据
 *
 * @author CH
 * @since 2025/9/4 14:22
 */
@Slf4j
@Intercepts(
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
)
public class DeleteOnlySelfInterceptor implements Interceptor {

    private final AuthService authService;

    public DeleteOnlySelfInterceptor(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 拦截MyBatis的删除操作，在SQL执行前添加创建人过滤条件
     *
     * @param invocation MyBatis调用上下文对象，包含执行的方法和参数
     *                   invocation.getArgs()[0] 为 MappedStatement 对象，包含SQL信息
     *                   invocation.getArgs()[1] 为 方法参数对象，可能为实体对象或Map
     * @return 原始方法的执行结果
     * @throws Throwable 当反射操作或方法执行出现异常时抛出
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 获取MappedStatement对象，包含SQL相关信息
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        // 获取方法参数对象
        Object parameter = invocation.getArgs()[1];

        // 1. 只拦截DELETE类型的SQL操作
        if (ms.getSqlCommandType() != SqlCommandType.DELETE) {
            return invocation.proceed();
        }

        // 2. 获取Mapper接口方法上的@DeleteOnlySelf注解
        Method mapperMethod = getMapperMethod(ms);
        if (mapperMethod == null) {
            return invocation.proceed();
        }
        DeleteOnlySelfOnMapper anno = mapperMethod.getAnnotation(DeleteOnlySelfOnMapper.class);
        if (anno == null) {
            return invocation.proceed();
        }

        // 3. 从参数中获取当前登录用户ID
        String loginUserId = authService.getCurrentUser().getUserId();
        if (loginUserId == null) {
            log.warn("未找到当前登录人ID，跳过权限控制");
            return invocation.proceed();
        }

        // 4. 在原始SQL基础上追加创建人过滤条件，使用参数化查询防止SQL注入
        // 例如：原始SQL为"DELETE FROM login WHERE id = ?"，追加后变为"DELETE FROM login WHERE id = ? AND create_by = ?"
        BoundSql boundSql = ms.getBoundSql(parameter);
        String createUserColumn = anno.createUserColumn();
        String newSql = boundSql.getSql() + " AND " + createUserColumn + " = ?";
        updateBoundSql(boundSql, newSql, loginUserId);

        return invocation.proceed();
    }

    /**
     * 根据MappedStatement的ID获取对应的Mapper接口方法
     *
     * @param ms MappedStatement对象，包含SQL的唯一标识ID
     *           例如：ID为"com.example.mapper.UserMapper.deleteUser"
     * @return 对应的Mapper接口方法，如果未找到则返回null
     */
    private Method getMapperMethod(MappedStatement ms) {
        try {
            String id = ms.getId();
            int last = id.lastIndexOf('.');
            if (last <= 0) {
                return null;
            }
            // 获取Mapper接口类名，例如："com.example.mapper.UserMapper"
            Class<?> mapper = ClassUtils.forName(id.substring(0, last));
            // 获取方法名，例如："deleteUser"
            String methodName = id.substring(last + 1);
            for (Method m : mapper.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
        } catch (Exception e) {
            log.debug("无法加载 Mapper 类: {}", ms.getId(), e);
        }
        return null;
    }

    /**
     * 更新BoundSql的SQL语句和参数，使用参数化查询防止SQL注入
     *
     * @param boundSql BoundSql对象，包含待执行的SQL语句
     * @param newSql   新的SQL语句，包含参数占位符
     *                 例如："DELETE FROM login WHERE id = ? AND create_by = ?"
     * @param userId   用户ID参数值
     */
    private void updateBoundSql(BoundSql boundSql, String newSql, String userId) {
        try {
            // 使用ClassUtils替代直接反射
            Field sqlField = BoundSql.class.getDeclaredField("sql");
            ClassUtils.setAccessible(sqlField);
            sqlField.set(boundSql, newSql);

            // 更新参数映射列表
            Field parameterMappingsField = BoundSql.class.getDeclaredField("parameterMappings");
            ClassUtils.setAccessible(parameterMappingsField);
            @SuppressWarnings("unchecked")
            List<ParameterMapping> parameterMappings = (List<ParameterMapping>) parameterMappingsField.get(boundSql);
            if (parameterMappings == null) {
                parameterMappings = new ArrayList<>();
            } else {
                parameterMappings = new ArrayList<>(parameterMappings);
            }

            // 添加新的参数映射
            ParameterMapping newMapping = new ParameterMapping.Builder(
                    boundSql.getConfiguration(),
                    "userId",
                    String.class
            ).build();
            parameterMappings.add(newMapping);
            parameterMappingsField.set(boundSql, parameterMappings);

            // 更新参数对象
            Field additionalParametersField = BoundSql.class.getDeclaredField("additionalParameters");
            ClassUtils.setAccessible(additionalParametersField);
            @SuppressWarnings("unchecked")
            Map<String, Object> additionalParameters = (Map<String, Object>) additionalParametersField.get(boundSql);
            if (additionalParameters == null) {
                additionalParameters = new java.util.HashMap<>();
                additionalParametersField.set(boundSql, additionalParameters);
            }
            additionalParameters.put("userId", userId);
        } catch (Exception e) {
            log.error("更新BoundSql失败", e);
            throw new RuntimeException("更新BoundSql失败", e);
        }
    }
}
