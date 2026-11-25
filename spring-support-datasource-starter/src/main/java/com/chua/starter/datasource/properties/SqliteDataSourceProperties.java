package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sqlite 嵌入式数据库配置属性。
 * <p>
 * 支持通过 {@code plugin.sqlite.*} 配置：
 * <pre>{@code
 * plugin.sqlite.enable=true
 * plugin.sqlite.path=./data/app.sqlite
 * plugin.sqlite.init-sql=classpath:db/init.sql
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.sqlite", ignoreInvalidFields = true)
public class SqliteDataSourceProperties {

    /**
     * 配置前缀常量
    */
    public static final String PRE = "plugin.sqlite";

    /**
     * 是否启用 sqlite 引擎自动配置
     * 默认值: false
     * 示例: plugin.sqlite.enable=true
    */
    private boolean enable = false;

    /**
     * sqlite 数据库文件路径，如 {@code ./data/app.sqlite} 或绝对路径
     * 默认值: ./data/app.sqlite
     * 示例: plugin.sqlite.path=./data/app.sqlite
    */
    private String path = "./data/app.sqlite";

    /**
     * SQL 初始化脚本路径，支持 {@code classpath:} 前缀，如 {@code classpath:db/init/sqlite}
     * 默认值: ""
     * 示例: plugin.sqlite.init-sql=classpath:db/init/sqlite
    */
    private String initSql = "";

    /**
     * 最大连接数
     * 默认值: 5
     * 示例: plugin.sqlite.max-pool-size=5
    */
    private int maxPoolSize = 5;
}
