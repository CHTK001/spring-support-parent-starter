package com.chua.starter.mybatis.interceptor;

import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.mybatis.annotations.DeleteOnlySelfOnMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        // 例如：参数Map中包含key为"currentUserId"的值，值为1001L
        String loginUserId = authService.getCurrentUser().getUserId();
        if (loginUserId == null) {
            log.warn("未找到当前登录人ID，跳过权限控制");
            return invocation.proceed();
        }

        // 4. 在原始SQL基础上追加创建人过滤条件
        // 例如：原始SQL为"DELETE FROM login WHERE id = ?"，追加后变为"DELETE FROM login WHERE id = ? AND create_by = 1001"
        BoundSql boundSql = ms.getBoundSql(parameter);
        String newSql = boundSql.getSql() + " AND " + anno.createUserColumn() + " = " + loginUserId;
        setSql(boundSql, newSql);

        return invocation.proceed();
    }

    /**
     * 根据MappedStatement的ID获取对应的Mapper接口方法
     *
     * @param ms MappedStatement对象，包含SQL的唯一标识ID
     *           例如：ID为"com.example.mapper.UserMapper.deleteUser"
     * @return 对应的Mapper接口方法
     * @throws Exception 当无法找到对应方法时抛出异常
     */
    private Method getMapperMethod(MappedStatement ms) throws Exception {
        String id = ms.getId();
        int last = id.lastIndexOf('.');
        // 获取Mapper接口类名，例如："com.example.mapper.UserMapper"
        Class<?> mapper = Class.forName(id.substring(0, last));
        // 获取方法名，例如："deleteUser"
        String methodName = id.substring(last + 1);
        for (Method m : mapper.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 从参数对象中获取指定名称的参数值
     *
     * @param parameter 方法参数对象，通常为Map或实体对象
     *                  例如：{id=1, currentUserId=1001, name="张三"}
     * @param name      要获取的参数名称
     *                  例如："currentUserId"
     * @param <T>       泛型参数，表示返回值类型
     * @return 指定名称的参数值，如果未找到则返回null
     */
    @SuppressWarnings("unchecked")
    private <T> T getParam(Object parameter, String name) {
        if (parameter instanceof Map) {
            return (T) ((Map<String, Object>) parameter).get(name);
        }
        return null;
    }

    /**
     * 通过反射修改BoundSql中的SQL语句
     *
     * @param boundSql BoundSql对象，包含待执行的SQL语句
     * @param sql      新的SQL语句
     *                 例如："DELETE FROM login WHERE id = ? AND create_by = 1001"
     * @throws Exception 当反射操作失败时抛出异常
     */
    private void setSql(BoundSql boundSql, String sql) throws Exception {
        Field field = BoundSql.class.getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, sql);
    }
}
