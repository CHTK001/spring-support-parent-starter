package com.chua.starter.plugin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * SQLite 数据库配置
 * 
 * @author CH
 * @since 2025/1/16
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.chua.starter.plugin.repository",
    entityManagerFactoryRef = "sqliteEntityManagerFactory",
    transactionManagerRef = "sqliteTransactionManager"
)
public class SqliteConfiguration {

    /**
     * SQLite 数据源配置
     */
    @Bean
    @ConfigurationProperties(prefix = "plugin.sqlite.datasource")
    public DataSource sqliteDataSource() {
        org.springframework.boot.jdbc.DataSourceBuilder<?> builder = 
            org.springframework.boot.jdbc.DataSourceBuilder.create();
        builder.driverClassName("org.sqlite.JDBC");
        builder.url("jdbc:sqlite:plugin.db");
        return builder.build();
    }

    /**
     * SQLite EntityManagerFactory
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean sqliteEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(sqliteDataSource());
        em.setPackagesToScan("com.chua.starter.plugin.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.SQLiteDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        em.setJpaProperties(properties);
        
        return em;
    }

    /**
     * SQLite 事务管理器
     */
    @Bean
    public PlatformTransactionManager sqliteTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(sqliteEntityManagerFactory().getObject());
        return transactionManager;
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
