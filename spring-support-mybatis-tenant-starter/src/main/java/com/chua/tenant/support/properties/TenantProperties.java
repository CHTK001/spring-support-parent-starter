package com.chua.tenant.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

import static com.chua.tenant.support.properties.TenantProperties.PRE;

/**
 * 租户配置
 * @author CH
 * @since 2024/9/11
 * @see com.chua.starter.mybatis.pojo.SysTenantBase
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class TenantProperties {


    public static final String PRE = "plugin.mybatis-plus.tenant";


    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 自动租户列数据源
     */
    private Set<String> autoTenantColumnDataSource = new HashSet<>();

    /**
     * 忽略的表
     */
    private Set<String> ignoreTable = new HashSet<>();

    /**
     * 租户ID字段名
     */
    private String tenantId = "sys_tenant_id";
}
