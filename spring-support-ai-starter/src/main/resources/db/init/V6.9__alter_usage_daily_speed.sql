-- =====================================================================
-- V6.9__alter_usage_daily_speed.sql
-- 类型: ALTER 列
-- 说明: 日汇总表补「输出速度」合计列，让「每日模型趋势」能直接读汇总表。
--
--   背景：/v2/usage/model-trend 需要按天按模型的平均输出速度，而
--   sys_ai_usage_daily 之前只存了调用数/令牌/费用/延迟合计，缺速度，
--   于是该接口即使大部分区间已被日汇总覆盖，也只能回落到 120 万行的
--   sys_ai_usage 做 GROUP BY（实测全时间 42 秒）。
--
--   写入方是 UsageAggregationTask（按天先删后插），读取方是
--   SysAiUsageServiceImpl#getDailyModelTrend。速度均值按
--   SUM(速度) / SUM(样本数) 还原，而不是对日均值再取算术平均，
--   否则样本稀少的模型会被放大。
--
--   写法仿照 V6.2：information_schema 判断 + 动态 DDL，
--   在 MySQL 8.4 / MariaDB 11.8 可安全重复执行。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage_daily'
          AND COLUMN_NAME = 'sys_ai_usage_daily_total_tokens_per_second'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage_daily`
             ADD COLUMN `sys_ai_usage_daily_total_tokens_per_second` DOUBLE DEFAULT 0
                 COMMENT ''输出速度(Token/秒)合计，配合样本数还原均值''
                 AFTER `sys_ai_usage_daily_total_first_token_latency`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage_daily'
          AND COLUMN_NAME = 'sys_ai_usage_daily_tps_samples'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage_daily`
             ADD COLUMN `sys_ai_usage_daily_tps_samples` BIGINT DEFAULT 0
                 COMMENT ''有实测输出速度的调用数，作为均值权重''
                 AFTER `sys_ai_usage_daily_total_tokens_per_second`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
