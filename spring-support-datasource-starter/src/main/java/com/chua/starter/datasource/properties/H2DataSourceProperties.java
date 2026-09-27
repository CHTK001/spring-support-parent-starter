package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * H2 嵌入式数据库配置属性。
 * <p>
 * 支持通过 {@code plugin.h2.*} 或 {@code spring.datasource.h2.*} 配置：
 * <pre>{@code
 * plugin.h2.enable=true
 * plugin.h2.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
 * plugin.h2.script-path=classpath:db/migration/h2
 * plugin.h2.cleanup-after-test=true
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.h2", ignoreInvalidFields = true)
public class H2DataSourceProperties {

    /**
     * 配置前缀常量
    */
    public static final String PRE = "plugin.h2";

    /**
     * 是否启用 H2 引擎自动配置
    */
    private boolean enable = false;

    /**
     * H2 JDBC URL，如 {@code jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1}
    */
    private String url = "jdbc:h2:mem:springh2db;DB_CLOSE_DELAY=-1";

    /**
     * 用户名（默认 sa）
    */
    private String username = "sa";

    /**
     * 密码（默认空）
    */
    private String password = "";

    /**
     * 迁移脚本路径，支持 类路径: 前缀，如 {@code classpath:db/migration/h2}
    */
    private String scriptPath = "";

    /**
     * 是否在应用关闭时清理所有用户表（仅测试环境建议开启）
    */
    private boolean cleanupAfterShutdown = false;

    /**
     * 是否在测试结束后清理数据（通过 Spring 测试 生命周期绑定）
    */
    private boolean cleanupAfterTest = false;
}
