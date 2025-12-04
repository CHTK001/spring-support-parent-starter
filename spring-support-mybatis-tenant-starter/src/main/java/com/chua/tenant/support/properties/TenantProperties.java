package com.chua.tenant.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

import static com.chua.tenant.support.properties.TenantProperties.PRE;

/**
 * 租户配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/9/11
 * @see com.chua.starter.mybatis.pojo.SysTenantBase
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class TenantProperties {

    public static final String PRE = "plugin.mybatis-plus.tenant";

    /**
     * 是否启用租户功能
     */
    private boolean enable = false;

    /**
     * 是否自动添加租户字段
     * 警告：此功能会自动修改数据库表结构，生产环境请谨慎使用
     */
    private boolean autoAddColumn = false;

    /**
     * 忽略的表
     * 这些表不会被添加租户字段，也不会被租户拦截器过滤
     */
    private Set<String> ignoreTable = new HashSet<>();

    /**
     * 租户ID字段名
     */
    private String tenantId = "sys_tenant_id";

    /**
     * 租户同步配置
     * <p>
     * 注意：同步协议的基础配置（如 host、port、protocol 等）请使用 plugin.sync.* 配置
     * 此处仅保留租户特有的配置
     * </p>
     *
     * @see com.chua.sync.support.properties.SyncProperties
     */
    private TenantSync tenantSync = new TenantSync();

    /**
     * 租户同步配置类
     */
    @Data
    public static class TenantSync {

        /**
         * 是否启用租户同步
         * <p>
         * 启用后会注册 TenantSyncMessageHandler 处理租户相关主题
         * 同时需要启用 plugin.sync.enable=true
         * </p>
         */
        private boolean enable = false;

        /**
         * 默认租户ID（客户端使用）
         */
        private String defaultTenantId;
    }
}
