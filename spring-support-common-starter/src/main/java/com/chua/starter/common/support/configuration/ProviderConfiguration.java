package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.CoreProperties;
import com.chua.starter.common.support.provider.ArrangeProvider;
import com.chua.starter.common.support.provider.DemoArrangeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 校验码配置
 *
 * @author CH
 */
@ComponentScan(basePackageClasses = DemoArrangeHandler.class)
public class ProviderConfiguration {


    @Bean
    @ConditionalOnProperty(prefix = CoreProperties.PRE, name = "open-arrange", matchIfMissing = true, havingValue = "true")
    @ConditionalOnMissingBean
    public ArrangeProvider arrangeProvider() {
        return new ArrangeProvider();
    }
}
