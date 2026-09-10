-- =====================================================================
-- AI 用量表增量迁移: sys_ai_usage 补充 call_time 索引 (spring-support-ai-starter)
-- 目标库: MySQL 8.0+
-- 说明: /v2/usage/list 分页查询 (selectPage) 生成 COUNT(*) 与
--       ORDER BY sys_ai_usage_call_time DESC, 表无该列索引时两者均全表扫描,
--       明细表每日增长数千行, 慢 SQL (阈值 500ms) 持续告警。
-- 幂等性: MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS (MariaDB 语法),
--       此处用 information_schema 条件判断 + PREPARE/EXECUTE 动态执行,
--       重复启动安全。H2 测试库不兼容 PREPARE 语法时该脚本被
--       continueOnError 跳过 (测试库数据量小, 无需该索引)。
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_ai_usage'
      AND index_name = 'idx_sys_ai_usage_call_time'
);
SET @create_idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_sys_ai_usage_call_time ON sys_ai_usage (sys_ai_usage_call_time)',
    'SELECT ''idx_sys_ai_usage_call_time already exists, skipped'''
);
PREPARE stmt FROM @create_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
