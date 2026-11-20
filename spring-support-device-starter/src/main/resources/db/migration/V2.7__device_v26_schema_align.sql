-- =====================================================================
-- V2.7__device_v26_schema_align.sql - 存量库结构对齐 V2.6（列名规范化 + 审计列）
--
-- 背景：V2.6 将设备库收敛为「1+1 两文件制」全量建表入口（列名规范化：
--   device_credential_* -> device_provider_credential_*、device_metric_* ->
--   device_metric_latest_*），并把 21 张业务表的审计列统一对齐 SysBase
--   （create_time/update_time/create_by/create_name/update_by/update_name）。
--   V2.6 全部为 CREATE TABLE IF NOT EXISTS，对已按旧结构建好的存量库不生效，
--   实体查询新列名时报 Unknown column，相关接口 500。
--
-- 本文件把存量库缺的对象按 V2.6 口径补齐：
--   1) device_provider_credential：26 个旧列名 RENAME 规范化
--   2) device_metric_latest：8 个旧列名 RENAME 规范化
--   3) credential.app_secret MODIFY 扩长到 VARCHAR(512)（SK 加密后长度变长）
--   4) 21 张业务表补齐 6 审计列
--
-- 写法约束（先例 V2.3__device_schema_repair / V4.0.0.59__device_credential_schedule_columns）：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；存储过程 / DELIMITER 会被
--   FlywayLike 拆坏。这里用 information_schema 判存在 + PREPARE 动态 DDL，
--   条件不满足时执行 DO 0，可重复执行（幂等）。
--
-- 口径：只 ADD / RENAME / MODIFY，不 DROP 旧列（旧 *_created_at/*_updated_at
--   列保留，实体不映射即无影响）；不回填历史审计数据。
-- 幂等：全部语句可反复执行；新库（V2.6 已建全量结构）执行时全部走 DO 0。
-- =====================================================================

-- 列名规范化: device_credential_app_key -> device_provider_credential_app_key
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_app_key')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_app_key'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_app_key` TO `device_provider_credential_app_key`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_app_secret -> device_provider_credential_app_secret
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_app_secret')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_app_secret'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_app_secret` TO `device_provider_credential_app_secret`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_base_url -> device_provider_credential_base_url
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_base_url')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_base_url'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_base_url` TO `device_provider_credential_base_url`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_device_sync_enabled -> device_provider_credential_device_sync_enabled
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_device_sync_enabled')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_device_sync_enabled'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_device_sync_enabled` TO `device_provider_credential_device_sync_enabled`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_device_sync_interval -> device_provider_credential_device_sync_interval
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_device_sync_interval')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_device_sync_interval'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_device_sync_interval` TO `device_provider_credential_device_sync_interval`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_enabled -> device_provider_credential_enabled
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_enabled')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_enabled'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_enabled` TO `device_provider_credential_enabled`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_event_poll_enabled -> device_provider_credential_event_poll_enabled
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_event_poll_enabled')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_event_poll_enabled'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_event_poll_enabled` TO `device_provider_credential_event_poll_enabled`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_event_poll_interval -> device_provider_credential_event_poll_interval
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_event_poll_interval')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_event_poll_interval'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_event_poll_interval` TO `device_provider_credential_event_poll_interval`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_group_no -> device_provider_credential_group_no
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_group_no')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_group_no'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_group_no` TO `device_provider_credential_group_no`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_id -> device_provider_credential_id
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_id'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_id` TO `device_provider_credential_id`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_is_default -> device_provider_credential_is_default
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_is_default')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_is_default'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_is_default` TO `device_provider_credential_is_default`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_last_test_at -> device_provider_credential_last_test_at
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_last_test_at')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_last_test_at'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_last_test_at` TO `device_provider_credential_last_test_at`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_last_test_ok -> device_provider_credential_last_test_ok
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_last_test_ok')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_last_test_ok'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_last_test_ok` TO `device_provider_credential_last_test_ok`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_metric_collect_enabled -> device_provider_credential_metric_collect_enabled
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_metric_collect_enabled')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_metric_collect_enabled'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_metric_collect_enabled` TO `device_provider_credential_metric_collect_enabled`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_metric_collect_interval -> device_provider_credential_metric_collect_interval
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_metric_collect_interval')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_metric_collect_interval'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_metric_collect_interval` TO `device_provider_credential_metric_collect_interval`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_name -> device_provider_credential_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_name')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_name'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_name` TO `device_provider_credential_name`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_offline_detect_enabled -> device_provider_credential_offline_detect_enabled
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_offline_detect_enabled')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_offline_detect_enabled'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_offline_detect_enabled` TO `device_provider_credential_offline_detect_enabled`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_offline_detect_interval -> device_provider_credential_offline_detect_interval
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_offline_detect_interval')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_offline_detect_interval'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_offline_detect_interval` TO `device_provider_credential_offline_detect_interval`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_offline_threshold -> device_provider_credential_offline_threshold
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_offline_threshold')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_offline_threshold'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_offline_threshold` TO `device_provider_credential_offline_threshold`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_org_sync_enabled -> device_provider_credential_org_sync_enabled
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_org_sync_enabled')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_org_sync_enabled'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_org_sync_enabled` TO `device_provider_credential_org_sync_enabled`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_org_sync_interval -> device_provider_credential_org_sync_interval
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_org_sync_interval')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_org_sync_interval'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_org_sync_interval` TO `device_provider_credential_org_sync_interval`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_platform_type -> device_provider_credential_platform_type
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_platform_type')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_platform_type'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_platform_type` TO `device_provider_credential_platform_type`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_product_code -> device_provider_credential_product_code
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_product_code')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_product_code'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_product_code` TO `device_provider_credential_product_code`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_project_id -> device_provider_credential_project_id
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_project_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_project_id'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_project_id` TO `device_provider_credential_project_id`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_remark -> device_provider_credential_remark
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_remark')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_remark'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_remark` TO `device_provider_credential_remark`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_credential_vendor -> device_provider_credential_vendor
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_vendor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_vendor'),
    'ALTER TABLE `device_provider_credential` RENAME COLUMN `device_credential_vendor` TO `device_provider_credential_vendor`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_info_id -> device_metric_latest_device_id
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_info_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_device_id'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_info_id` TO `device_metric_latest_device_id`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_collected_at -> device_metric_latest_collected_at
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_collected_at')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_collected_at'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_collected_at` TO `device_metric_latest_collected_at`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_data_time -> device_metric_latest_data_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_data_time')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_data_time'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_data_time` TO `device_metric_latest_data_time`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_id -> device_metric_latest_id
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_id'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_id` TO `device_metric_latest_id`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_key -> device_metric_latest_key
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_key')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_key'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_key` TO `device_metric_latest_key`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_name -> device_metric_latest_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_name')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_name'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_name` TO `device_metric_latest_name`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_unit -> device_metric_latest_unit
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_unit')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_unit'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_unit` TO `device_metric_latest_unit`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 列名规范化: device_metric_value -> device_metric_latest_value
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_value')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_latest_value'),
    'ALTER TABLE `device_metric_latest` RENAME COLUMN `device_metric_value` TO `device_metric_latest_value`',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- app_secret 扩长: VARCHAR(512)（SK 加密后长度变长）
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_provider_credential_app_secret'),
    'ALTER TABLE `device_provider_credential` MODIFY COLUMN `device_provider_credential_app_secret` VARCHAR(512) COMMENT ''AppSecret（SK），加密存储''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_access_record.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_access_record` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_access_record.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_access_record` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_access_record.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_access_record` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_access_record.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_access_record` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_access_record.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_access_record` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_access_record.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_access_record` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_alarm.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_alarm` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_alarm.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_alarm` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_alarm.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_alarm` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_alarm.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_alarm` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_alarm.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_alarm` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_alarm.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_alarm` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_attendance_record.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_attendance_record` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_attendance_record.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_attendance_record` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_attendance_record.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_attendance_record` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_attendance_record.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_attendance_record` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_attendance_record.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_attendance_record` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_attendance_record.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_attendance_record` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_barrier_gate.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_barrier_gate` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_barrier_gate.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_barrier_gate` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_barrier_gate.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_barrier_gate` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_barrier_gate.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_barrier_gate` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_barrier_gate.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_barrier_gate` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_barrier_gate.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_barrier_gate` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_channel.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_channel` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_channel.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_channel` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_channel.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_channel` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_channel.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_channel` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_channel.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_channel` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_channel.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_channel` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_door.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_door` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_door.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_door` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_door.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_door` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_door.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_door` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_door.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_door` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_door.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_door` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_event_record.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_event_record` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_event_record.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_event_record` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_event_record.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_event_record` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_event_record.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_event_record` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_event_record.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_event_record` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_event_record.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_event_record` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_group` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_group` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_group` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_group` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_group` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_group` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group_device.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_group_device` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group_device.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_group_device` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group_device.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_group_device` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group_device.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_group_device` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group_device.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_group_device` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_group_device.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_group_device` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_info.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_info` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_info.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_info` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_info.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_info` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_info.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_info` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_info.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_info` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_info.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_info` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_metric_latest.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_metric_latest` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_metric_latest.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_metric_latest` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_metric_latest.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_metric_latest` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_metric_latest.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_metric_latest` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_metric_latest.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_metric_latest` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_metric_latest.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_metric_latest` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_organization.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_organization` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_organization.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_organization` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_organization.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_organization` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_organization.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_organization` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_organization.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_organization` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_organization.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_organization` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_person.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_person` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_person.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_person` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_person.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_person` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_person.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_person` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_person.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_person` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_person.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_person` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_preset.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_preset` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_preset.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_preset` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_preset.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_preset` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_preset.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_preset` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_preset.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_preset` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_preset.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_preset` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_provider_credential.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_provider_credential.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_provider_credential.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_provider_credential.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_provider_credential.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_provider_credential.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_task_state.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_task_state` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_task_state.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_task_state` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_task_state.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_task_state` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_task_state.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_task_state` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_task_state.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_task_state` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_task_state.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_task_state` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_vehicle` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_vehicle` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_vehicle` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_vehicle` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_vehicle` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_vehicle` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_vehicle_pass` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_vehicle_pass` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_vehicle_pass` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_vehicle_pass` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_vehicle_pass` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_vehicle_pass` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass_cursor.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_vehicle_pass_cursor` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass_cursor.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_vehicle_pass_cursor` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass_cursor.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_vehicle_pass_cursor` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass_cursor.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_vehicle_pass_cursor` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass_cursor.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_vehicle_pass_cursor` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_vehicle_pass_cursor.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_vehicle_pass_cursor` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_appkey.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_webhook_appkey` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_appkey.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_webhook_appkey` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_appkey.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_webhook_appkey` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_appkey.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_webhook_appkey` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_appkey.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_webhook_appkey` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_appkey.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_webhook_appkey` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_receipt.create_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'create_time'),
    'ALTER TABLE `device_webhook_receipt` ADD COLUMN `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_receipt.update_time
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'update_time'),
    'ALTER TABLE `device_webhook_receipt` ADD COLUMN `update_time` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_receipt.create_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'create_by'),
    'ALTER TABLE `device_webhook_receipt` ADD COLUMN `create_by` INT NULL DEFAULT NULL COMMENT ''创建人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_receipt.create_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'create_name'),
    'ALTER TABLE `device_webhook_receipt` ADD COLUMN `create_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_receipt.update_by
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'update_by'),
    'ALTER TABLE `device_webhook_receipt` ADD COLUMN `update_by` INT NULL DEFAULT NULL COMMENT ''更新人 ID''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;

-- 补审计列: device_webhook_receipt.update_name
SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'update_name'),
    'ALTER TABLE `device_webhook_receipt` ADD COLUMN `update_name` VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''',
    'DO 0'));
PREPARE align_stmt FROM @ddl;
EXECUTE align_stmt;
DEALLOCATE PREPARE align_stmt;
