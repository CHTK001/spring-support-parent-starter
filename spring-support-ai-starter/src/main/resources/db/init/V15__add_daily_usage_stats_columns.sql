-- V15__add_daily_usage_stats_columns.sql
-- 为 sys_ai_usage_daily 日汇总表补充统计列：
--   cached_tokens   缓存读取 Token 合计
--   total_latency   总耗时（毫秒）合计，用于计算平均耗时
--   standard_cost   标准费用（官方价格，未折扣）合计

ALTER TABLE `sys_ai_usage_daily`
    ADD COLUMN `sys_ai_usage_daily_total_cached_tokens` INT DEFAULT 0 COMMENT '缓存读取 Token 合计' AFTER `sys_ai_usage_daily_total_cost`,
    ADD COLUMN `sys_ai_usage_daily_total_latency` BIGINT DEFAULT 0 COMMENT '总耗时(ms)合计' AFTER `sys_ai_usage_daily_total_cached_tokens`,
    ADD COLUMN `sys_ai_usage_daily_standard_cost` DOUBLE DEFAULT 0 COMMENT '标准费用(官方价格)' AFTER `sys_ai_usage_daily_total_latency`;

SELECT 1;