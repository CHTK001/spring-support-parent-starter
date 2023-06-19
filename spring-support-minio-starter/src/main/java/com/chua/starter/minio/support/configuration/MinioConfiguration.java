package com.chua.starter.minio.support.configuration;

import com.chua.starter.minio.support.properties.MinioProperties;
import com.chua.starter.minio.support.provider.MinioProvider;
import com.chua.starter.minio.support.template.MinioTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * minio
 * @author CH
 */
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {
    /**
     * minio template
     * @param minioProperties minio config
     * @return template
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${plugin.spring.minio.address:}')")
    public MinioTemplate minioTemplate(MinioProperties minioProperties) {
        return new MinioTemplate(minioProperties);
    }


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${plugin.spring.minio.address:}')")
    public MinioProvider MinioProvider(MinioTemplate minioTemplate) {
        return new MinioProvider(minioTemplate);
    }
}
