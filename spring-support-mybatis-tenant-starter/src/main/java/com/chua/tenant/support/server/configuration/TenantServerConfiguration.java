package com.chua.tenant.support.server.configuration;

import com.chua.tenant.support.common.properties.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 租户服务端配置
 * <p>
 * 服务端模式下，启用租户管理功能和数据下发
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@MapperScan("com.chua.tenant.support.server.mapper")
@ConditionalOnProperty(prefix = TenantProperties.PRE, name = {"enable", "mode"}, havingValue = "server", matchIfMissing = false)
public class TenantServerConfiguration {

    private final TenantProperties tenantProperties;

    @PostConstruct
    public void init() {
        log.info("[租户服务端] 服务端模式已启用");
        log.info("[租户服务端] 租户同步: {}", tenantProperties.getSync().isEnable() ? "已启用" : "已禁用");
    }
}
