package com.chua.starter.datasource.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 多数据源事务注解
 * <p>
 * 替代 Spring 原生 {@code @Transactional}，用于在动态多数据源场景下保证
 * 数据源上下文在事务方法中正确切换和管理。
 * </p>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>并<strong>不试图实现跨数据源 ACID</strong> —— 跨库一致性请使用 Seata {@code @GlobalTransactional}</li>
 *   <li>核心作用是确保 {@code @DS} 数据源切换在事务绑定<strong>之前</strong>完成</li>
 *   <li>通过栈式（Deque）上下文管理，支持嵌套方法调用中的 DS 上下文恢复</li>
 *   <li>跟踪事务内实际使用了哪些数据源，便于后续与 Seata 集成</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @DSTransactional
 * @DS("master")
 * @Service
 * public class UserService {
 *
 *     public void save(User user) {
 *         // 使用 master 数据源，事务正常回滚
 *         userDao.insert(user);
 *
 *         // 切换到 slave 做查询（嵌套切换，返回后自动恢复 master）
 *         UserLog log = logService.getLastLog(user.getId());
 *
 *         // 继续使用 master 做后续操作
 *         userDao.updateLog(user.getId(), log.getId());
 *     }
 * }
 * }</pre>
 *
 * @since 2026/07/14
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DSTransactional {

    /**
     * 事务名称（用于日志和监控）
     *
     * @return 事务名称
     */
    @AliasFor("name")
    String value() default "";

    /**
     * 事务名称
     *
     * @return 事务名称
     */
    @AliasFor("value")
    String name() default "";

    /**
     * 是否传播当前数据源上下文
     * <p>如果为 true，嵌套调用时继承外部数据源上下文；<br>
     * 如果为 false，嵌套调用时创建一个独立的数据源上下文（调用结束后恢复）。</p>
     *
     * @return 是否传播
     */
    boolean propagate() default true;
}
