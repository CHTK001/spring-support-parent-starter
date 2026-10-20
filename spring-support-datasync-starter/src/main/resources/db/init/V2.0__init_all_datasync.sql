-- =====================================================================
-- V2.0__init_all_datasync.sql - spring-support-datasync-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。此前分散的
--   V1.0__init_all_datasync.sql 已在本次收敛中并入本文件并删除。
-- =====================================================================
-- DataSync 数据库模式：datasync_mapping 映射主表
CREATE TABLE IF NOT EXISTS datasync_mapping (
    datasync_mapping_id VARCHAR(255) PRIMARY KEY,
    datasync_mapping_input_id VARCHAR(255),
    datasync_mapping_source_id VARCHAR(255),
    datasync_mapping_output_id VARCHAR(255),
    datasync_mapping_sink_id VARCHAR(255),
    datasync_mapping_batch INT DEFAULT 100,
    datasync_mapping_cron_type VARCHAR(255),
    datasync_mapping_cron VARCHAR(255),
    datasync_mapping_params TEXT,
    datasync_mapping_enabled BOOLEAN DEFAULT TRUE,
    datasync_mapping_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    datasync_mapping_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- DataSync 数据库模式：datasync_field_mapping 字段映射表
CREATE TABLE IF NOT EXISTS datasync_field_mapping (
    datasync_field_mapping_id VARCHAR(255) PRIMARY KEY,
    datasync_field_mapping_mapping_id VARCHAR(255),
    datasync_field_mapping_source_field VARCHAR(255),
    datasync_field_mapping_target_field VARCHAR(255),
    datasync_field_mapping_converter VARCHAR(255),
    datasync_field_mapping_sort INT,
    FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES datasync_mapping(datasync_mapping_id)
);
