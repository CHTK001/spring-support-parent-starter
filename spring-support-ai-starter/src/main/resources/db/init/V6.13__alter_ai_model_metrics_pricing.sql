-- =====================================================================
-- 模型指标库增加峰谷 / 按 N 次定价规则（V6.13）
--
-- 为什么单独成文件：V6.11 已被执行过的库不会再跑第二遍（FlywayLike 按
-- 脚本名 + 版本记账，success 后跳过），补列必须放在新版本脚本里才会生效。
-- 建表侧（V6.11 CREATE TABLE）已含该列；本脚本只负责存量库补列。
--
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态 DDL。
-- MySQL 8.4 / MariaDB 11.8 均不支持 ADD COLUMN IF NOT EXISTS（报 1064），
-- 故补列一律用动态 DDL；可反复执行，重复运行只走 SELECT 1 分支。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model_metrics'
          AND COLUMN_NAME = 'sys_ai_model_metrics_pricing_rules'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model_metrics` ADD COLUMN `sys_ai_model_metrics_pricing_rules` TEXT NULL COMMENT ''峰谷 / 按 N 次定价规则 JSON'' AFTER `sys_ai_model_metrics_manual_locked`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
