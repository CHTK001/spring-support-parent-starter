package com.chua.starter.common.support.configuration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 记录上下文aware
 *
 * @author CH
 * @version 1.0.0
 * @since 2020/11/28
 */
@Configuration
public class ApplicationAwareApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        SpringBeanUtils.setApplicationContext(applicationContext);
    }
}
