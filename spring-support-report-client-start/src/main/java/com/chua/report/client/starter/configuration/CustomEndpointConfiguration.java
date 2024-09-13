package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.spring.endpoint.MapEndpoint;
import com.chua.report.client.starter.spring.endpoint.ThreadEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * endpoint配置
 * @author CH
 * @since 2024/9/13
 */
public class CustomEndpointConfiguration {


    /**
     * map
     * @return MapEndpoint
     */
    @Bean("mapEndpoint-1")
    public MapEndpoint mapEndpoint() {
        return new MapEndpoint();
    }
    /**
     * threadEndpoint
     * @return ThreadEndpoint
     */
    @Bean("threadEndpoint-1")
    public ThreadEndpoint threadEndpoint() {
        return new ThreadEndpoint();
    }
}
