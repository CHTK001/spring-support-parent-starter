-- V13__fix_data_source_timestamps.sql
-- 修复 sys_ai_data_source 表的 create_time/update_time 列类型从 DATETIME 改为 BIGINT

ALTER TABLE `sys_ai_data_source` MODIFY COLUMN `create_time` BIGINT NULL COMMENT '创建时间戳（epoch ms）';
ALTER TABLE `sys_ai_data_source` MODIFY COLUMN `update_time` BIGINT NULL COMMENT '更新时间戳（epoch ms）';
