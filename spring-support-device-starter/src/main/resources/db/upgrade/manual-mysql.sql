-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

CREATE INDEX `idx_device_info_org` ON `device_info` (`device_info_org_id`);
CREATE INDEX `idx_device_info_location` ON `device_info` (`device_info_latitude`, `device_info_longitude`);
ALTER TABLE `device_info` ADD COLUMN `device_info_latitude` DECIMAL(10, 6);
ALTER TABLE `device_info` ADD COLUMN `device_info_longitude` DECIMAL(10, 6);
ALTER TABLE `device_info` ADD COLUMN `device_info_address` VARCHAR(255);
ALTER TABLE `device_info` ADD COLUMN `device_info_serial` VARCHAR(64);
ALTER TABLE `device_info` ADD COLUMN `device_info_firmware` VARCHAR(32);
ALTER TABLE `device_info` ADD COLUMN `device_info_channel_count` INT DEFAULT 0;
ALTER TABLE `device_info` ADD COLUMN `device_info_offline_threshold` INT DEFAULT 60;
ALTER TABLE `device_info` ADD COLUMN `device_info_updated_at` DATETIME;
ALTER TABLE `device_channel` ADD COLUMN `device_channel_status` VARCHAR(20) DEFAULT 'enabled';
ALTER TABLE `device_channel` ADD COLUMN `device_channel_sort` INT DEFAULT 0;
