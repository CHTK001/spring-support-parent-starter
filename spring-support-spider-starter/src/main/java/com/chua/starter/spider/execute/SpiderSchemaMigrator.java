package com.chua.starter.spider.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 爬虫模块数据库增量迁移执行器。
 *
 * <p>对已存在的库补列增量字段，避免叶 FlywayLike 脚本在表结构已存在时被跳过
 * 导致实体字段与表列不一致。当前补 {@code spider_definition.executor_type} 列。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class SpiderSchemaMigrator implements ApplicationRunner {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderSchemaMigrator.class);

    /**
     * 数据源
     */
    private final DataSource dataSource;

    /**
     * 构造迁移执行器。
     *
     * @param dataSource 数据源
     */
    public SpiderSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 启动后执行增量迁移。
     *
     * @param args 应用参数
     */
    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ensureColumn(jdbc, "spider_definition", "executor_type",
                "VARCHAR(16) DEFAULT NULL COMMENT '执行类型（ONCE 一次 / SCHEDULE 定时）'",
                "cron_expression");
        ensureColumn(jdbc, "spider_definition", "entry_url",
                "VARCHAR(2048) DEFAULT NULL COMMENT '入口 URL'",
                "last_message");
        ensureColumn(jdbc, "spider_definition", "site_host",
                "VARCHAR(512) DEFAULT NULL COMMENT '站点主机'",
                "entry_url");
        ensureColumn(jdbc, "spider_definition", "collect_deep_url",
                "TINYINT(1) DEFAULT 0 COMMENT '是否深度采集'",
                "site_host");
        ensureColumn(jdbc, "spider_definition", "max_depth",
                "INT DEFAULT 1 COMMENT '最大深度'",
                "collect_deep_url");
        ensureColumn(jdbc, "spider_definition", "max_pages",
                "INT DEFAULT 100 COMMENT '最大页数'",
                "max_depth");
        ensureColumn(jdbc, "spider_definition", "executor_rounds",
                "INT DEFAULT 1 COMMENT '执行轮次'",
                "executor_type");
        ensureColumn(jdbc, "spider_definition", "remark",
                "VARCHAR(512) DEFAULT NULL COMMENT '备注'",
                "executor_rounds");
        ensureColumn(jdbc, "spider_flow", "code",
                "VARCHAR(64) DEFAULT NULL COMMENT '流程编码（前端 flowCode）'",
                "name");
        ensureColumn(jdbc, "spider_flow", "enabled",
                "TINYINT(1) DEFAULT 1 COMMENT '是否启用（1 启用 / 0 停用）'",
                "code");
    }

    /**
     * 确保目标表存在指定列，不存在则追加。
     *
     * @param jdbc           JDBC 模板
     * @param table          表名
     * @param column         列名
     * @param definition     列定义（含类型与注释）
     * @param afterColumn    AFTER 列，可空
     */
    private void ensureColumn(JdbcTemplate jdbc, String table, String column,
                              String definition, String afterColumn) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (count != null && count > 0) {
            return;
        }
        StringBuilder ddl = new StringBuilder("ALTER TABLE `")
                .append(table).append("` ADD COLUMN `").append(column)
                .append("` ").append(definition);
        if (afterColumn != null && !afterColumn.isBlank()) {
            ddl.append(" AFTER `").append(afterColumn).append('`');
        }
        try {
            jdbc.execute(ddl.toString());
            log.info("数据库增量迁移成功: {} 新增列 {}", table, column);
        } catch (Exception e) {
            log.warn("数据库增量迁移失败（可能已存在）: {} {} -> {}", table, column, e.getMessage());
        }
    }
}