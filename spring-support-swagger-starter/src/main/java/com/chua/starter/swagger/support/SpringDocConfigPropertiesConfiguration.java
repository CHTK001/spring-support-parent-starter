package com.chua.starter.swagger.support;

import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc 配置属性兜底注册。
 * <p>
 * Knife4j 的自动配置会直接依赖 {@link SpringDocConfigProperties} Bean。
 * 在部分场景下（例如未触发 SpringDoc 的自动配置条件），该 Bean 可能不存在，
 * 会导致应用启动失败。这里提供一个缺省 Bean，避免因缺参导致启动中断。
 * </p>
 *
 * @author CH
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SpringDocConfigProperties.class)
public class SpringDocConfigPropertiesConfiguration {

    /**
     * 注册 SpringDocConfigProperties Bean（兜底）。
     *
     * @return SpringDoc 配置属性
     */
    @Bean
    @ConditionalOnMissingBean(SpringDocConfigProperties.class)
    public SpringDocConfigProperties springDocConfigProperties() {
        return new SpringDocConfigProperties();
    }
}


