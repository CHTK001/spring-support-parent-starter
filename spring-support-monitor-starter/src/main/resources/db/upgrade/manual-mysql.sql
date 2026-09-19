-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

CREATE INDEX `idx_ms_monitor_server_host` ON `ms_monitor` (`ms_server_host_id`);
ALTER TABLE `ms_monitor` ADD COLUMN `ms_monitor_agent_key` VARCHAR(64) NULL COMMENT '探针标识(Agent推送鉴权)';
ALTER TABLE `ms_monitor` ADD COLUMN `ms_server_host_id` BIGINT NULL COMMENT '关联服务器主机 ID(ms_server_host.id, 资产联动)';
