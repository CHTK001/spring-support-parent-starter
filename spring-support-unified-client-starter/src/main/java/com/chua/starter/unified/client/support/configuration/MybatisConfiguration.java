package com.chua.starter.unified.client.support.configuration;

import com.chua.starter.unified.client.support.mybatis.SupportInjector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis配置
 *
 * @author CH
 * @since 2023/11/20
 */
public class MybatisConfiguration {

    /**
     * SupportInjector
     *
     * @return SupportInjector
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.injector.DefaultSqlInjector")
    public SupportInjector supportInjector() {
        return new SupportInjector();
    }
}
