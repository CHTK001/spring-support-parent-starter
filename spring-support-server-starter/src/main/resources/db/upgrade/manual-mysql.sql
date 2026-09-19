-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_gpu_count` INT NULL COMMENT 'GPU卡数(探测结果)';
ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_gpu_model` VARCHAR(256) NULL COMMENT 'GPU型号(探测结果)';
ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_networks` TEXT NULL COMMENT '网卡信息(JSON数组)';
ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_cpu_usage` DECIMAL(5,2) NULL COMMENT 'CPU使用率%(探测结果)';
ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_mem_usage` DECIMAL(5,2) NULL COMMENT '内存使用率%(探测结果)';
ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_disk_usage` DECIMAL(5,2) NULL COMMENT '磁盘使用率%(探测结果)';
