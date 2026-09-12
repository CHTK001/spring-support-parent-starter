package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.support.DSTransactionalAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@code @DSTransactional} 注解的自动配置
 * <p>
 * 当检测到 {@link DSTransactionalAspect} 所依赖的类路径时，
 * 自动注册 {@code @DSTransactional} 注解的处理切面。
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class DSTransactionalConfiguration {

    /**
     * 注册 {@code @DSTransactional} 事务切面 Bean
     *
     * @return 事务切面实例
     */
    @Bean
    @ConditionalOnMissingBean(DSTransactionalAspect.class)
    public DSTransactionalAspect dsTransactionalAspect() {
        /**
          * dstransactionalaspect。
         * @return DSTransactionalAspect的结果
         */
        return new DSTransactionalAspect();
    }
}
