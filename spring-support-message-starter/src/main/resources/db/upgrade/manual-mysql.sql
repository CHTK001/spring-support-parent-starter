-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

CREATE UNIQUE INDEX `uk_provider_code` ON `sys_message_provider` (`sys_message_provider_code`);
CREATE INDEX `idx_provider_type` ON `sys_message_provider` (`sys_message_provider_type`);
CREATE INDEX `idx_provider_status` ON `sys_message_provider` (`sys_message_provider_status`);
CREATE INDEX `idx_template_provider` ON `sys_message_template` (`sys_message_provider_id`);
CREATE INDEX `idx_template_code` ON `sys_message_template` (`sys_message_template_code`);
CREATE INDEX `idx_template_status` ON `sys_message_template` (`sys_message_template_status`);
CREATE INDEX `idx_log_target` ON `sys_message_log` (`sys_message_log_target`);
CREATE INDEX `idx_log_template` ON `sys_message_log` (`sys_message_template_id`);
CREATE INDEX `idx_log_provider` ON `sys_message_log` (`sys_message_provider_id`);
CREATE INDEX `idx_log_create_time` ON `sys_message_log` (`create_time`);
CREATE INDEX `idx_log_track_token` ON `sys_message_log` (`sys_message_track_token`);
CREATE INDEX `idx_log_batch` ON `sys_message_log` (`sys_message_batch_id`);
