-- =====================================================================
-- V6.2__alter_model_metrics_avg_speed.sql
-- 类型: ALTER 列
-- 说明: 补全 V5.1 全量建表时未生效的列 avg_output_speed_tps。
--
--   V5.1 已标记为执行成功，但 V5.1 的 CREATE TABLE IF NOT EXISTS 对存量的
--   sys_ai_model_metrics 表未产生任何列变更（本地旧表只包含
--   sys_ai_model_metrics_output_speed_tps）。
--   须向本地模型指标模型中追加 sys_ai_model_metrics_avg_output_speed_tps，
--   与 sys_ai_model_metrics_output_speed_tps 并列，
--   @see SysAiModelMetricsService.syncAvgOutputSpeedTps().
--
-- 写法仿照 V6.1：info_schema.COLUMNS 判断 + 动态 DDL，
-- 在 MySQL 8.4 / MariaDB 11.8 可安全重复执行，不依赖 checksum。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model_metrics'
          AND COLUMN_NAME = 'sys_ai_model_metrics_avg_output_speed_tps'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model_metrics` ADD COLUMN `sys_ai_model_metrics_avg_output_speed_tps` DOUBLE DEFAULT NULL
             COMMENT ''本地平均输出速度 token/秒'' AFTER `sys_ai_model_metrics_output_speed_tps`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
