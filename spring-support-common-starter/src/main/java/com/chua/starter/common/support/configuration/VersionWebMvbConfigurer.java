package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.IpProperties;
import com.chua.starter.common.support.properties.VersionProperties;
import com.chua.starter.common.support.version.ApiVersionRequestMappingHandlerMapping;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Web版本MVB配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        VersionProperties.class,
        IpProperties.class,
})
@Slf4j
public class VersionWebMvbConfigurer implements WebMvcRegistrations {

    @Resource
    private VersionProperties versionProperties;

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        if (versionProperties.isEnable()) {
            log.info(">>>>>>> 开启版本控制功能");
            return new ApiVersionRequestMappingHandlerMapping();
        }
        return new RequestMappingHandlerMapping();
    }

}
