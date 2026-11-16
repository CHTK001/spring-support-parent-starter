-- =====================================================================
-- 模型指标库增加流式 / 异步能力位（V6.15）
--
-- 为什么单独成文件：V6.11 已被执行过的库不会再跑第二遍（FlywayLike 按
-- 脚本名 + 版本记账，success 后跳过），补列必须放在新版本脚本里才会生效。
-- 建表侧（V6.11 CREATE TABLE）已含这两列；本脚本只负责存量库补列。
--
-- 口径：NULL = 未收录（不是「不支持」）。流式按协议判定（openai / anthropic /
-- gemini 且为远端地址），异步按厂商官方 Batch / 后台任务能力判定。
--
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态 DDL。
-- MySQL 8.4 / MariaDB 11.8 均不支持 ADD COLUMN IF NOT EXISTS（报 1064），
-- 故补列一律用动态 DDL；可反复执行，重复运行只走 SELECT 1 分支。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model_metrics'
          AND COLUMN_NAME = 'sys_ai_model_metrics_support_streaming'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model_metrics` ADD COLUMN `sys_ai_model_metrics_support_streaming` TINYINT(1) NULL COMMENT ''是否支持流式输出'' AFTER `sys_ai_model_metrics_support_function_call`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model_metrics'
          AND COLUMN_NAME = 'sys_ai_model_metrics_support_async'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model_metrics` ADD COLUMN `sys_ai_model_metrics_support_async` TINYINT(1) NULL COMMENT ''是否支持异步调用（Batch / 后台任务）'' AFTER `sys_ai_model_metrics_support_streaming`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
