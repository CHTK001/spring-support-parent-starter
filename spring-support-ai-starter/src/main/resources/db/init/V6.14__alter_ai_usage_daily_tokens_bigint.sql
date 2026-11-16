-- =====================================================================
-- 日汇总表 token 列扩容：INT → BIGINT（V6.14）
--
-- 为什么需要：全量回填在 2026-11-16 报
--   Data truncation: Out of range value for column
--   'sys_ai_usage_daily_total_prompt_tokens' at row 1
-- 三方同步的历史用量按「天 + 厂商 + 模型」汇总后，单组 prompt tokens 可到
-- 29.5 亿（当日约 9 成是缓存命中，仍计入 prompt），超出 INT 上限 2147483647。
-- 汇总列是明细多行的求和，天然比明细行大一个量级，必须与明细表的 BIGINT 对齐。
--
-- 为什么单独成文件：V6.11 已被执行过的库不会再跑第二遍（FlywayLike 按脚本名 +
-- 版本记账，success 后跳过），改列类型必须放在新版本脚本里才会生效。
-- 建表侧（V6.11 CREATE TABLE）保持原样，本脚本只负责存量库扩列。
--
-- 写法：查 information_schema.COLUMNS 判类型，再用 PREPARE/EXECUTE 执行动态
-- DDL；可反复执行，字段已是 BIGINT 时只走 SELECT 1 分支。
-- 注：MODIFY COLUMN 必须带上原 COMMENT，否则会丢注释。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage_daily'
          AND COLUMN_NAME = 'sys_ai_usage_daily_total_prompt_tokens' AND DATA_TYPE = 'bigint'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage_daily` MODIFY COLUMN `sys_ai_usage_daily_total_prompt_tokens` BIGINT NULL COMMENT '' Token'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage_daily'
          AND COLUMN_NAME = 'sys_ai_usage_daily_total_completion_tokens' AND DATA_TYPE = 'bigint'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage_daily` MODIFY COLUMN `sys_ai_usage_daily_total_completion_tokens` BIGINT NULL COMMENT '' Token'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage_daily'
          AND COLUMN_NAME = 'sys_ai_usage_daily_total_tokens' AND DATA_TYPE = 'bigint'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage_daily` MODIFY COLUMN `sys_ai_usage_daily_total_tokens` BIGINT NULL COMMENT '' Token'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage_daily'
          AND COLUMN_NAME = 'sys_ai_usage_daily_total_cached_tokens' AND DATA_TYPE = 'bigint'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage_daily` MODIFY COLUMN `sys_ai_usage_daily_total_cached_tokens` BIGINT DEFAULT 0 COMMENT ''缓存读取 Token 合计'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
