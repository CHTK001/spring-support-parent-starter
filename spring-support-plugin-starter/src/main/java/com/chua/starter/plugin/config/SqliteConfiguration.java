package com.chua.starter.plugin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQLite 数据库配置
 * 内部使用，不注入 Bean
 *
 * @author CH
 * @since 2025/1/16
 */
public class SqliteConfiguration {

    /**
     * 获取默认的 SQLite 数据库路径
     *
     * @return 数据库路径
     */
    public static String getDefaultDatabasePath() {
        return "plugin.db";
    }

    /**
     * 获取 SQLite JDBC URL
     *
     * @param databasePath 数据库文件路径
     * @return JDBC URL
     */
    public static String getSqliteJdbcUrl(String databasePath) {
        return "jdbc:sqlite:" + databasePath;
    }

    /**
     * 获取 SQLite 驱动类名
     *
     * @return 驱动类名
     */
    public static String getSqliteDriverClassName() {
        return "org.sqlite.JDBC";
    }

    /**
     * 检查 SQLite 驱动是否可用
     *
     * @return 是否可用
     */
    public static boolean isSqliteDriverAvailable() {
        try {
            Class.forName(getSqliteDriverClassName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * SQLite 配置属性
     */
    @Data
    @ConfigurationProperties(prefix = "plugin.sqlite")
    public static class SqliteProperties {
        
        /**
         * 是否启用SQLite
         */
        private boolean enabled = true;
        
        /**
         * 数据库文件路径
         */
        private String databasePath = "plugin.db";
        
        /**
         * 连接池配置
         */
        private PoolConfig pool = new PoolConfig();
        
        @Data
        public static class PoolConfig {
            /**
             * 最大连接数
             */
            private int maxActive = 10;
            
            /**
             * 最小空闲连接数
             */
            private int minIdle = 1;
            
            /**
             * 最大空闲连接数
             */
            private int maxIdle = 5;
            
            /**
             * 连接超时时间(毫秒)
             */
            private long maxWait = 30000;
        }
    }
}
