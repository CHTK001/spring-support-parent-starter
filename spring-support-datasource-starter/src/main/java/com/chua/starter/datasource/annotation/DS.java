package com.chua.starter.datasource.annotation;

import java.lang.annotation.*;

/**
 * 数据源切换注解
 * <p>
 * 用于标注在类或方法上，指定使用的数据源名称。
 * 方法级别注解优先级高于类级别。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * // 类级别 - 该类下所有方法默认使用指定数据源
 * &#64;DS("slave")
 * &#64;Service
 * public class UserService {
 *     // 使用slave数据源
 *     public User getById(Long id) { ... }
 *     
 *     // 方法级别覆盖类级别配置，使用master数据源
 *     &#64;DS("master")
 *     public void save(User user) { ... }
 * }
 * 
 * // 多数据源聚合查询
 * &#64;DS("master,slave")
 * public List&lt;User&gt; queryAll() { ... }
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {
    
    /**
     * 数据源名称
     * <p>
     * 支持单个数据源名称或多个数据源名称（逗号分隔）。
     * 多数据源时会创建聚合数据源。
     * </p>
     *
     * @return 数据源名称
     */
    String value();
}
