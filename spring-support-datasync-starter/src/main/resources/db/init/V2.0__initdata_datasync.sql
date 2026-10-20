-- =====================================================================
-- V2.0__initdata_datasync.sql - spring-support-datasync-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 默认映射模板（datasync_mapping / datasync_field_mapping 各一行）在此灌入。
--   此前分散的 V1.0__initdata_all_datasync.sql 已在本次收敛中并入本文件并删除。
-- =====================================================================

INSERT IGNORE INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('db-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);

INSERT IGNORE INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('f1', 'db-user-sync', 'name', 'user_name', 'toString', 0);
