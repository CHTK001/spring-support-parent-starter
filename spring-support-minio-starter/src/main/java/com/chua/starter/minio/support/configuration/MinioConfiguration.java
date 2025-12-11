package com.chua.starter.minio.support.configuration;

import com.chua.starter.minio.support.properties.MinioProperties;
import com.chua.starter.minio.support.template.MinioTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.@ConditionalOnProperty(prefix = "plugin.minio", name = "enable", havingValue = "true", matchIfMissing = false)
autoconfigure.condition.ConditionalOnProperty;

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

}
