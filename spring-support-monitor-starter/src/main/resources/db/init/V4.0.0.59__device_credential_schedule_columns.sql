-- ---------------------------------------------------------------------
-- V4.0.0.59: 设备凭据级检测/同步调度列 - 存量库补列
--
-- 背景：V2.0__init_all_device.sql 已包含这些列，但存量库在 V2.0 基线执行后
-- 才追加该段，Flyway 不会重跑已成功的 V2.0，导致 DeviceScheduleManager
-- 查询 device_provider_credential 时报 Unknown column。
--
-- 写法约束：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；存储过程 / DELIMITER
-- 会被 FlywayLike 拆坏。这里使用 information_schema 判存在 + PREPARE
-- 动态 DDL，列已存在时执行 DO 0，可重复执行。
-- ---------------------------------------------------------------------

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_offline_detect_enabled'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_offline_detect_enabled` TINYINT DEFAULT 0 COMMENT ''该凭据维度的在离线检测开关''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_offline_detect_interval'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_offline_detect_interval` INT NULL COMMENT ''该凭据维度的在离线检测间隔（秒）''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_event_poll_enabled'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_event_poll_enabled` TINYINT DEFAULT 0 COMMENT ''该凭据实例的事件轮询开关''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_event_poll_interval'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_event_poll_interval` INT NULL COMMENT ''该凭据实例的事件轮询间隔（秒）''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_device_sync_enabled'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_device_sync_enabled` TINYINT DEFAULT 0 COMMENT ''该凭据维度的设备自动同步开关''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_device_sync_interval'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_device_sync_interval` INT NULL COMMENT ''该凭据维度的设备自动同步间隔（秒）''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_org_sync_enabled'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_org_sync_enabled` TINYINT DEFAULT 0 COMMENT ''该凭据维度的组织自动同步开关''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_org_sync_interval'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_org_sync_interval` INT NULL COMMENT ''该凭据维度的组织自动同步间隔（秒）''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_last_test_at'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_last_test_at` DATETIME NULL COMMENT ''最近一次连通性探测时间''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'device_provider_credential'
             AND COLUMN_NAME = 'device_credential_last_test_ok'),
    'ALTER TABLE `device_provider_credential` ADD COLUMN `device_credential_last_test_ok` TINYINT NULL COMMENT ''最近一次连通性探测结果：0 失败、1 成功、NULL 未检测''',
    'DO 0'));
PREPARE dc_schedule_stmt FROM @ddl;
EXECUTE dc_schedule_stmt;
DEALLOCATE PREPARE dc_schedule_stmt;
