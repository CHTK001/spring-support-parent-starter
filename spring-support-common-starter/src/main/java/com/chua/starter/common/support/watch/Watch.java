package com.chua.starter.common.support.watch;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法追踪注解
 * <p>标注在方法或类上，启用链路追踪和性能监控。</p>
 *
 * @author CH
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Watch {

    /**
     * 追踪描述，用于在链路日志中标识该节点的业务含义
     */
    String value() default "";

    /**
     * 超时阈值（毫秒），超过此值将打印 WARN 日志；0 表示不限制
     */
    long timeoutMs() default 0;

    /**
     * 是否记录方法入参（默认关闭，避免敏感数据泄露）
     */
    boolean logArgs() default false;

    /**
     * 是否记录方法返回值（默认关闭）
     */
    boolean logResult() default false;
}
