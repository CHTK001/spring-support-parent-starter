package com.chua.starter.ai.support.usage.spi;

import com.chua.common.support.datasearch.usage.database.UsageRowProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sys_ai_usage 用量行提供者 — {@link UsageRowProvider} 的 ai 模块实现。
 *
 * <p>sys_ai_usage 表归 ai 模块所有：表名、列名与查询 SQL 收敛在本类，
 * utils 层的 DatabaseUsageParser 经 SPI 取数，不再内嵌本表结构。</p>
 *
 * <p>开关与 utils 侧 DatabaseUsageParser 一致：
 * {@code ai.usage.database.enabled=true} 且类路径存在 DataSource 时装配。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Component
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "ai.usage.database", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SysAiUsageRowProvider implements UsageRowProvider {

    /**
     * 查询 SQL（sys_ai_usage 表结构，仅 total_tokens 非空的记录，按调用时间倒序）
     */
    // [P3C 1.4 豁免] 编译期常量字符串，全部为代码内字面量，无外部输入
    private static final String QUERY_SQL =
            "SELECT sys_ai_usage_provider, sys_ai_usage_model," +
            "       sys_ai_usage_prompt_tokens, sys_ai_usage_completion_tokens," +
            "       sys_ai_usage_total_tokens, sys_ai_usage_total_cost," +
            "       sys_ai_usage_input_cost, sys_ai_usage_output_cost," +
            "       sys_ai_usage_call_time" +
            "  FROM sys_ai_usage" +
            " WHERE sys_ai_usage_total_tokens IS NOT NULL" +
            " ORDER BY sys_ai_usage_call_time DESC";

    /**
     * 物理列名 → 逻辑字段名映射（SPI 契约键）
     */
    private static final Map<String, String> COLUMN_TO_FIELD = new LinkedHashMap<>();

    static {
        COLUMN_TO_FIELD.put("sys_ai_usage_provider", UsageRowProvider.PROVIDER);
        COLUMN_TO_FIELD.put("sys_ai_usage_model", UsageRowProvider.MODEL);
        COLUMN_TO_FIELD.put("sys_ai_usage_prompt_tokens", UsageRowProvider.INPUT_TOKENS);
        COLUMN_TO_FIELD.put("sys_ai_usage_completion_tokens", UsageRowProvider.OUTPUT_TOKENS);
        COLUMN_TO_FIELD.put("sys_ai_usage_total_tokens", UsageRowProvider.TOTAL_TOKENS);
        COLUMN_TO_FIELD.put("sys_ai_usage_call_time", UsageRowProvider.START_TIME);
        COLUMN_TO_FIELD.put("sys_ai_usage_total_cost", UsageRowProvider.COST);
        COLUMN_TO_FIELD.put("sys_ai_usage_input_cost", UsageRowProvider.INPUT_COST);
        COLUMN_TO_FIELD.put("sys_ai_usage_output_cost", UsageRowProvider.OUTPUT_COST);
    }

    /**
     * JDBC 模板
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数。
     *
     * @param dataSource 数据源
     */
    public SysAiUsageRowProvider(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public List<Map<String, Object>> queryRows() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(QUERY_SQL);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>(row.size());
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                mapped.put(COLUMN_TO_FIELD.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
            }
            result.add(mapped);
        }
        return result;
    }
}
