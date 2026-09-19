-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

CREATE INDEX `idx_filesystem_upload_chunk_upload_id` ON `filesystem_upload_chunk` (`filesystem_upload_chunk_upload_id`);
CREATE INDEX `idx_filesystem_upload_merge_log_upload_id` ON `filesystem_upload_merge_log` (`filesystem_upload_merge_log_upload_id`);
CREATE INDEX `idx_filesystem_upload_upload_log_upload_id` ON `filesystem_upload_upload_log` (`filesystem_upload_upload_log_upload_id`);
