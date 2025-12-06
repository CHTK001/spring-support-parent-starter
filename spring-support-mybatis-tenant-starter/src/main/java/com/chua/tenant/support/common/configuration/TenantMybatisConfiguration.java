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
public class TenantMybatisConfiguration {

    private final TenantProperties tenantProperties;

    /**
     * 配置 MyBatis-Plus 租户拦截器（客户端模式）
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnProperty(prefix = TenantProperties.PRE + ".client", name = "enable", havingValue = "true")
    public MybatisPlusInterceptor tenantClientMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandlerImpl(tenantProperties, true));
        interceptor.addInnerInterceptor(tenantInterceptor);

        log.info("[租户配置] 客户端租户拦截器已启用，租户字段: {}", tenantProperties.getClient().getTenantIdColumn());
        return interceptor;
    }

    /**
     * 配置 MyBatis-Plus 租户拦截器（服务端模式）
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnProperty(prefix = TenantProperties.PRE + ".server", name = "enable", havingValue = "true")
    public MybatisPlusInterceptor tenantServerMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandlerImpl(tenantProperties, false));
        interceptor.addInnerInterceptor(tenantInterceptor);

        log.info("[租户配置] 服务端租户拦截器已启用，租户字段: {}", tenantProperties.getServer().getTenantIdColumn());
        return interceptor;
    }
}
