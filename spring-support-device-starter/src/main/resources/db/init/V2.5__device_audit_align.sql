-- =====================================================================
-- V2.5__device_audit_align.sql - 设备模块审计字段统一对齐 SysBase
--
-- 背景：21 个设备实体统一继承 spring-support-mybatis-starter 的 SysBase
-- （create_name / create_by / create_time / update_time / update_name / update_by），
-- 实体上自带的 created_at / updated_at 审计字段已删除。本脚本把数据库对齐：
--
--   1) 列名规范化（字段以表名为前缀的既定规则）：
--      - device_provider_credential：device_credential_* -> device_provider_credential_*
--      - device_metric_latest：device_metric_* / device_info_id -> device_metric_latest_*
--   2) 审计列统一：各业务表的 created_at / updated_at 更名为 create_time / update_time
--      （RENAME COLUMN 保留数据与默认值/ON UPDATE 属性，索引随列自动跟随）
--   3) 补齐 SysBase 六审计列：缺 create_time / update_time / create_by / create_name /
--      update_by / update_name 的表按需补列。宿主进程未装配 MetaObjectHandler，
--      自动填充不生效，故新增列带数据库默认值兜底：
--      create_time DEFAULT CURRENT_TIMESTAMP；update_time ON UPDATE CURRENT_TIMESTAMP。
--      服务层在原赋值点改为显式 setCreateTime/setUpdateTime，行为与迁移前一致。
--
-- 说明：V2.4 为建表基线保持不动；本脚本全部语句先查 information_schema 再执行，
--       对已应用 V2.4 的存量库与全新库均可安全执行（幂等）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第一部分：列名规范化 + 审计列改名（created_at/updated_at -> create_time/update_time）
-- ---------------------------------------------------------------------

-- device_organization
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'device_organization_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_organization RENAME COLUMN device_organization_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_info
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_info RENAME COLUMN device_info_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_info RENAME COLUMN device_info_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_provider_credential：全部 28 列改为表名前缀；审计两列直接并入 create_time/update_time
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_id');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_id TO device_provider_credential_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_vendor');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_vendor TO device_provider_credential_vendor', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_platform_type');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_platform_type TO device_provider_credential_platform_type', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_name');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_name TO device_provider_credential_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_app_key');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_app_key TO device_provider_credential_app_key', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_app_secret');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_app_secret TO device_provider_credential_app_secret', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_product_code');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_product_code TO device_provider_credential_product_code', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_project_id');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_project_id TO device_provider_credential_project_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_group_no');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_group_no TO device_provider_credential_group_no', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_base_url');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_base_url TO device_provider_credential_base_url', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_offline_threshold');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_offline_threshold TO device_provider_credential_offline_threshold', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_is_default');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_is_default TO device_provider_credential_is_default', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_enabled');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_enabled TO device_provider_credential_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_remark');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_remark TO device_provider_credential_remark', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_offline_detect_enabled');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_offline_detect_enabled TO device_provider_credential_offline_detect_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_offline_detect_interval');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_offline_detect_interval TO device_provider_credential_offline_detect_interval', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_event_poll_enabled');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_event_poll_enabled TO device_provider_credential_event_poll_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_event_poll_interval');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_event_poll_interval TO device_provider_credential_event_poll_interval', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_device_sync_enabled');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_device_sync_enabled TO device_provider_credential_device_sync_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_device_sync_interval');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_device_sync_interval TO device_provider_credential_device_sync_interval', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_org_sync_enabled');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_org_sync_enabled TO device_provider_credential_org_sync_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_org_sync_interval');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_org_sync_interval TO device_provider_credential_org_sync_interval', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_metric_collect_enabled');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_metric_collect_enabled TO device_provider_credential_metric_collect_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_metric_collect_interval');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_metric_collect_interval TO device_provider_credential_metric_collect_interval', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_last_test_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_last_test_at TO device_provider_credential_last_test_at', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_last_test_ok');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_last_test_ok TO device_provider_credential_last_test_ok', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_provider_credential RENAME COLUMN device_credential_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_group（原 created_at/updated_at 为裸列名）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_group RENAME COLUMN created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_group RENAME COLUMN updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_group_device
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_group_device RENAME COLUMN created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_person
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'device_person_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_person RENAME COLUMN device_person_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'device_person_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_person RENAME COLUMN device_person_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_metric_latest：全部 8 列改为表名前缀
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_id');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_id TO device_metric_latest_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_info_id');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_info_id TO device_metric_latest_device_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_key');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_key TO device_metric_latest_key', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_name');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_name TO device_metric_latest_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_value');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_value TO device_metric_latest_value', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_unit');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_unit TO device_metric_latest_unit', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_data_time');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_data_time TO device_metric_latest_data_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'device_metric_collected_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_metric_latest RENAME COLUMN device_metric_collected_at TO device_metric_latest_collected_at', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_webhook_appkey
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'device_webhook_appkey_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_webhook_appkey RENAME COLUMN device_webhook_appkey_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_webhook_receipt
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'device_webhook_receipt_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_webhook_receipt RENAME COLUMN device_webhook_receipt_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_alarm
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'device_alarm_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_alarm RENAME COLUMN device_alarm_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_vehicle
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'device_vehicle_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_vehicle RENAME COLUMN device_vehicle_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'device_vehicle_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_vehicle RENAME COLUMN device_vehicle_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_barrier_gate
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'device_barrier_gate_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_barrier_gate RENAME COLUMN device_barrier_gate_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'device_barrier_gate_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_barrier_gate RENAME COLUMN device_barrier_gate_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_vehicle_pass
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'device_vehicle_pass_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_vehicle_pass RENAME COLUMN device_vehicle_pass_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_attendance_record
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'device_attendance_record_created_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_attendance_record RENAME COLUMN device_attendance_record_created_at TO create_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_vehicle_pass_cursor
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'device_vehicle_pass_cursor_updated_at');
SET @sql := IF(@has > 0, 'ALTER TABLE device_vehicle_pass_cursor RENAME COLUMN device_vehicle_pass_cursor_updated_at TO update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 第二部分：补齐 SysBase 审计列（缺哪列补哪列；已有则跳过）
-- ---------------------------------------------------------------------

-- device_organization（update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_organization ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_organization ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_info（create_time/update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_info ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_channel
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_channel ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_channel' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_channel ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_preset
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_preset ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_preset' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_preset ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_event_record
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_event_record ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_event_record' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_event_record ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_door
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_door ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_door' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_door ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_access_record
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_access_record ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_access_record' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_access_record ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_provider_credential（create_time/update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_provider_credential ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_task_state
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_task_state ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_task_state' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_task_state ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_group（create_time/update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_group ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_group_device（create_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_group_device ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_group_device' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_group_device ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_person（create_time/update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_person' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_person ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_metric_latest
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_metric_latest ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_metric_latest' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_metric_latest ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_webhook_appkey（create_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_webhook_appkey ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_appkey' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_webhook_appkey ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_webhook_receipt（create_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_webhook_receipt ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_webhook_receipt' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_webhook_receipt ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_alarm（create_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_alarm ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_alarm' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_alarm ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_vehicle（create_time/update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_vehicle ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_barrier_gate（create_time/update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_barrier_gate' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_barrier_gate ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_vehicle_pass（create_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_vehicle_pass ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_vehicle_pass ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_attendance_record（create_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_attendance_record ADD COLUMN update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_attendance_record' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_attendance_record ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- device_vehicle_pass_cursor（update_time 已由改名而来）
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@has = 0, 'ALTER TABLE device_vehicle_pass_cursor ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_vehicle_pass_cursor' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@has = 0, 'ALTER TABLE device_vehicle_pass_cursor ADD COLUMN create_by INT NULL DEFAULT NULL COMMENT ''创建人 ID'', ADD COLUMN create_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''创建人姓名'', ADD COLUMN update_by INT NULL DEFAULT NULL COMMENT ''更新人 ID'', ADD COLUMN update_name VARCHAR(64) NULL DEFAULT NULL COMMENT ''更新人姓名''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
