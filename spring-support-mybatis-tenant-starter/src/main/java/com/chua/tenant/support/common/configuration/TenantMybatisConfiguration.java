package com.chua.tenant.support.common.configuration;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.chua.tenant.support.common.properties.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 租户 MyBatis 配置
 * <p>
 * 配置租户拦截器，实现多租户数据隔离
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = TenantProperties.PRE, name = "enable", havingValue = "true")
public class TenantMybatisConfiguration {

    private final TenantProperties tenantProperties;

    /**
     * 配置 MyBatis-Plus 租户拦截器
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor tenantMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加租户拦截器
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandlerImpl(tenantProperties));
        interceptor.addInnerInterceptor(tenantInterceptor);

        log.info("[租户配置] 租户拦截器已启用，租户字段: {}", tenantProperties.getTenantIdColumn());
        return interceptor;
    }
}
