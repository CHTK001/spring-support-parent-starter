package com.chua.starter.milvus.support.configuration;

import com.chua.starter.milvus.support.properties.MilvusProperties;
import com.chua.starter.milvus.support.template.MilvusTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * minio
 * @author CH
 */
@EnableConfigurationProperties(MilvusProperties.class)
public class MilvusConfiguration {
    /**
     * minio template
     * @param milvusProperties minio config
     * @return template
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${plugin.spring.milvus.address:}')")
    public MilvusTemplate minioTemplate(MilvusProperties milvusProperties) {
        return new MilvusTemplate(milvusProperties);
    }
}
