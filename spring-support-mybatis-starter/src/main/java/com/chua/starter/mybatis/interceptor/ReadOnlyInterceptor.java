package com.chua.starter.mybatis.interceptor;

import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * 只读模式拦截器
 * 当开启只读模式时，拦截所有更新、插入、删除操作并抛出只读异常
 *
 * @author CH
 * @since 2024/12/04
 */
@Slf4j
@Intercepts(
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
)
public class ReadOnlyInterceptor implements Interceptor {

    private final MybatisPlusProperties mybatisPlusProperties;

    /**
     * 构造函数
     *
     * @param mybatisPlusProperties MyBatis配置属性
     */
    public ReadOnlyInterceptor(MybatisPlusProperties mybatisPlusProperties) {
        this.mybatisPlusProperties = mybatisPlusProperties;
    }

    /**
     * 拦截MyBatis的更新操作
     * 当处于只读模式时，拦截INSERT、UPDATE、DELETE操作并抛出只读异常
     *
     * @param invocation MyBatis调用上下文对象
     * @return 原始方法的执行结果
     * @throws Throwable 当只读模式下执行写操作时抛出ReadOnlyException
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 检查是否开启只读模式
        if (!mybatisPlusProperties.isReadOnly()) {
            return invocation.proceed();
        }

        // 获取MappedStatement对象
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        SqlCommandType sqlCommandType = ms.getSqlCommandType();

        // 拦截INSERT、UPDATE、DELETE操作
        if (sqlCommandType == SqlCommandType.INSERT
                || sqlCommandType == SqlCommandType.UPDATE
                || sqlCommandType == SqlCommandType.DELETE) {

            String statementId = ms.getId();
            log.warn("只读模式已开启，拒绝执行写操作: {} ({})", statementId, sqlCommandType);

            throw new ReadOnlyException(
                    String.format("数据库处于只读模式，禁止执行%s操作: %s", sqlCommandType.name(), statementId)
            );
        }

        return invocation.proceed();
    }
}
