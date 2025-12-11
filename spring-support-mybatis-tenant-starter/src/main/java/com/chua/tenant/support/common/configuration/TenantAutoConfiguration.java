package com.chua.tenant.support.common.configuration;

import com.chua.tenant.support.common.properties.TenantProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProper@ConditionalOnProperty(prefix = "plugin.tenant", name = "enable", havingValue = "true", matchIfMissing = false)
ty;

/**
 * 租户自动配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
@Import({
        TenantMybatisConfiguration.class
})
public class TenantAutoConfiguration {
}
