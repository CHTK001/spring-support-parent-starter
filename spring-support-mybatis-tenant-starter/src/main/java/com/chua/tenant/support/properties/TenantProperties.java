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
     * 同步协议配置
     */
    private SyncProtocol syncProtocol = new SyncProtocol();

    /**
     * 同步协议配置类
     */
    @Data
    public static class SyncProtocol {

        /**
         * 是否启用同步协议
         */
        private boolean enable = false;

        /**
         * 程序类型：server-服务端，client-客户端
         */
        private String type = "client";

        /**
         * 服务端端口（当type=server时生效）
         */
        private int serverPort = 19280;

        /**
         * 服务端地址（当type=client时生效）
         */
        private String serverAddress = "http://localhost:19280";

        /**
         * 元数据下发配置
         */
        private MetadataSync metadataSync = new MetadataSync();

        /**
         * 元数据同步配置
         */
        @Data
        public static class MetadataSync {

            /**
             * 是否启用元数据下发
             */
            private boolean enable = false;

            /**
             * 下发间隔时间（秒）
             */
            private int interval = 300;

            /**
             * 初始延迟时间（秒）
             */
            private int initialDelay = 60;
        }
    }
}
