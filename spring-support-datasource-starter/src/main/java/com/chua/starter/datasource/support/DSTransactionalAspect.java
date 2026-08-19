package com.chua.starter.datasource.support;

import com.chua.starter.datasource.annotation.DSTransactional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@code @DSTransactional} 注解的切面实现
 * <p>
 * 负责管理多数据源事务的生命周期：
 * <ul>
 *   <li>建立事务上下文，跟踪事务中使用的所有数据源</li>
 *   <li>为已使用的数据源建立非自动提交连接</li>
 *   <li>方法正常返回时提交所有连接</li>
 *   <li>方法抛出异常时回滚所有连接</li>
 *   <li>清理连接资源</li>
 * </ul>
 * </p>
 *
 * <h3>优先级说明</h3>
 * <p>
 * 优先级 ({@code @Order}) 必须高于 Spring 的 {@code TransactionInterceptor}
 * （默认最低优先级 {@code Ordered.LOWEST_PRECEDENCE}），
 * 以确保在 Spring 事务绑定之前完成数据源上下文建立。
 * 设置为 {@link Ordered#LOWEST_PRECEDENCE} - 10。
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class DSTransactionalAspect {

    /**
     * 环绕通知：拦截所有标注 {@code @DSTransactional} 的方法
     * <p>
     * 同时支持类级别和方法级别 {@code @DSTransactional} 注解。
     * 方法级别注解优先级高于类级别。
     * </p>
     *
     * @param pjp 连接点
     * @return 方法执行结果
     * @throws Throwable 任何异常都会触发回滚
     */
    @Around("execution(* *(..)) && " +
            "(@annotation(com.chua.starter.datasource.annotation.DSTransactional) || " +
            "@within(com.chua.starter.datasource.annotation.DSTransactional))")
    /** Around */
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 解析注解（方法优先 -> 类级别回退）
        DSTransactional dsTransactional = resolveAnnotation(pjp);
        if (dsTransactional == null) {
            return pjp.proceed();
        }

        // 获取事务名称
        String txName = getTransactionName(pjp, dsTransactional);

        // ===== 嵌套传播 =====
        // 如果已经在一个 @DSTransactional 上下文中且 propagate=true，直接执行
        MultiDSTransactionContext existing = MultiDSTransactionContext.getCurrent();
        if (existing != null && dsTransactional.propagate()) {
            MultiDSTransactionContext.incrementDepth();
            try {
                log.debug("[DSTX:{}] 嵌套执行 (深度 {}), 传播上下文: {}",
                        txName, MultiDSTransactionContext.getDepth(), existing);
                return pjp.proceed();
            } finally {
                MultiDSTransactionContext.decrementDepth();
            }
        }

        // ===== 新建事务 =====
        MultiDSTransactionContext context = new MultiDSTransactionContext(txName);
        MultiDSTransactionContext.setCurrent(context);

        long start = System.currentTimeMillis();
        log.info("[DSTX:{}] 开始事务 (方法: {})", txName, pjp.getSignature().toShortString());

        try {
            Object result = pjp.proceed();
            context.commit();
            log.info("[DSTX:{}] 事务提交成功 (耗时: {}ms, 数据源: {})",
                    txName, System.currentTimeMillis() - start, context.getUsedDataSources());
            return result;
        } catch (Throwable e) {
            log.warn("[DSTX:{}] 事务回滚 (耗时: {}ms, 异常: {})",
                    txName, System.currentTimeMillis() - start, e.getMessage());
            context.rollback();
            throw e;
        } finally {
            context.cleanup();
            MultiDSTransactionContext.setCurrent(null);
            log.debug("[DSTX:{}] 事务上下文清理完成", txName);
        }
    }

    /**
     * 解析 {@code @DSTransactional} 注解
     * <p>方法级别优先级高于类级别。</p>
     */
    private DSTransactional resolveAnnotation(ProceedingJoinPoint pjp) {
        // 方法级别
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        DSTransactional dsTransactional = AnnotatedElementUtils.findMergedAnnotation(method, DSTransactional.class);
        if (dsTransactional != null) {
            return dsTransactional;
        }
        // 类级别回退
        return AnnotatedElementUtils.findMergedAnnotation(pjp.getTarget().getClass(), DSTransactional.class);
    }

    /**
     * 获取事务名称
     */
    private String getTransactionName(ProceedingJoinPoint pjp, DSTransactional dsTransactional) {
        String name = dsTransactional.value();
        if (name == null || name.isEmpty()) {
            name = dsTransactional.name();
        }
        if (name == null || name.isEmpty()) {
            // 默认使用方法签名
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            name = signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
        }
        return name;
    }
}
