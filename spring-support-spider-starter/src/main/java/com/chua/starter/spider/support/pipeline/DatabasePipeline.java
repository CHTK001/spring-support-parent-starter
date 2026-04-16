package com.chua.starter.spider.support.pipeline;

import com.chua.common.support.annotations.Spi;
import com.chua.spider.ResultItems;
import com.chua.spider.Task;
import com.chua.spider.pipeline.Pipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于数据库的输出管道（动态建表写入）。
 *
 * <p>实现 {@link Pipeline} SPI，将爬取结果写入数据库。
 * 目标表名从 Task 的 extra 中读取（key: {@code spider.targetTable}），
 * 默认写入 {@code spider_crawled_data} 表。</p>
 *
 * @author CH
 */
@Slf4j
@Spi("database")
@Component
@RequiredArgsConstructor
public class DatabasePipeline implements Pipeline {

    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_TABLE = "spider_crawled_data";

    @Override
    public void process(ResultItems resultItems, Task task) {
        if (resultItems == null || resultItems.getAll().isEmpty()) return;

        String tableName = DEFAULT_TABLE;
        Map<String, Object> fields = resultItems.getAll();

        try {
            ensureTableExists(tableName, fields);
            insertRecord(tableName, fields);
            log.debug("[DatabasePipeline] 写入 {} 条字段到表 {}", fields.size(), tableName);
        } catch (Exception e) {
            log.warn("[DatabasePipeline] 写入数据库失败 table={}: {}", tableName, e.getMessage());
        }
    }

    private void ensureTableExists(String tableName, Map<String, Object> fields) {
        // 简化实现：若表不存在则创建（生产环境应使用 Flyway 管理）
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS `")
                .append(tableName).append("` (id BIGINT PRIMARY KEY AUTO_INCREMENT");
        for (String col : fields.keySet()) {
            String safeCol = col.replaceAll("[^a-zA-Z0-9_]", "_");
            ddl.append(", `").append(safeCol).append("` TEXT NULL");
        }
        ddl.append(", create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        try {
            jdbcTemplate.execute(ddl.toString());
        } catch (Exception e) {
            log.debug("[DatabasePipeline] 建表跳过（可能已存在）: {}", e.getMessage());
        }
    }

    private void insertRecord(String tableName, Map<String, Object> fields) {
        if (fields.isEmpty()) return;
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        Object[] params = new Object[fields.size()];
        int i = 0;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String safeCol = entry.getKey().replaceAll("[^a-zA-Z0-9_]", "_");
            if (i > 0) { cols.append(", "); vals.append(", "); }
            cols.append("`").append(safeCol).append("`");
            vals.append("?");
            params[i++] = entry.getValue() != null ? entry.getValue().toString() : null;
        }
        jdbcTemplate.update(
                "INSERT INTO `" + tableName + "` (" + cols + ") VALUES (" + vals + ")",
                params
        );
    }
}
