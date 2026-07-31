package com.chua.starter.datasync.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DataSync 配置属性。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.datasync")
public class DatasyncProperties {

    /**
     * 是否启用 DataSync
     */
    private boolean enabled = true;

    /**
     * 映射来源：file | database
     */
    private String mappingSource = "file";

    /**
     * 文件模式配置
     */
    private FileMapping fileMapping = new FileMapping();

    /**
     * 数据库模式配置
     */
    private DatabaseMapping databaseMapping = new DatabaseMapping();

    @Data
    public static class FileMapping {
        /**
         * 映射配置文件目录路径
         */
        private String directoryPath = "./datasync/mappings";
    }

    @Data
    public static class DatabaseMapping {
        /**
         * 是否启用数据库映射源
         */
        private boolean enabled = false;

        /**
         * 表名前缀
         */
        private String tablePrefix = "datasync_";

        /**
         * 是否自动初始化表结构
         */
        private boolean autoInitTable = true;

        /**
         * 数据库轮询间隔（Cron 表达式）
         */
        private String cron = "0/5 * * * * ?";
    }
}
