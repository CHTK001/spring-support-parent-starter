package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.ControlProperties;
import com.chua.starter.common.support.properties.IpProperties;
import com.chua.starter.common.support.version.ApiVersionRequestMappingHandlerMapping;
import jakarta.annotation.Priority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Web版本MVB配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        ControlProperties.class,
        IpProperties.class,
})
@Slf4j
@Priority(0)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ControlWebMvbConfigurer implements WebMvcRegistrations, EnvironmentAware {

    ControlProperties controlProperties;

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
       return requestMappingInfoHandlerMapping();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestMappingHandlerMapping requestMappingInfoHandlerMapping() {
        if (controlProperties.isEnable()) {
            log.info(">>>>>>> 开启版本控制功能");
            return new ApiVersionRequestMappingHandlerMapping(controlProperties);
        }
        return new RequestMappingHandlerMapping();
    }


    @Override
    public void setEnvironment(Environment environment) {
        this.controlProperties = Binder.get(environment).bindOrCreate(ControlProperties.PRE, ControlProperties.class);
    }
}
