-- =====================================================================
-- V1.0__init_all.sql - spring-support-filesystem-starter full DDL
-- merged from loose scripts:
--   V1.0__init_filesystem_upload.sql
--   V1.3__create_merge_upload_log_tables.sql
-- =====================================================================


-- ---------------------------------------------------------------------
-- source: V1.0__init_filesystem_upload.sql
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS filesystem_upload_task (

    filesystem_upload_task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filesystem_upload_task_upload_id VARCHAR(64) NOT NULL UNIQUE,
    filesystem_upload_task_file_name VARCHAR(255),
    filesystem_upload_task_original_name VARCHAR(255),
    filesystem_upload_task_file_size BIGINT,
    filesystem_upload_task_chunk_size BIGINT,
    filesystem_upload_task_total_chunks INT,
    filesystem_upload_task_uploaded_chunks INT DEFAULT 0,
    filesystem_upload_task_storage_type VARCHAR(50),
    filesystem_upload_task_bucket VARCHAR(100),
    filesystem_upload_task_key VARCHAR(255),
    filesystem_upload_task_content_type VARCHAR(100),
    filesystem_upload_task_status INT DEFAULT 0,
    filesystem_upload_task_stage INT DEFAULT 0,
    filesystem_upload_task_error_message VARCHAR(500),
    filesystem_upload_task_file_url VARCHAR(500),
    filesystem_upload_task_retry_count INT DEFAULT 0,
    filesystem_upload_task_start_time TIMESTAMP,
    filesystem_upload_task_complete_time TIMESTAMP,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50)
);

-- ---------------------------------------------------------------------
-- 分片上传详情表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS filesystem_upload_chunk (

    filesystem_upload_chunk_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filesystem_upload_chunk_upload_id VARCHAR(64) NOT NULL,
    filesystem_upload_chunk_part_number INT,
    filesystem_upload_chunk_size BIGINT,
    filesystem_upload_chunk_etag VARCHAR(128),
    filesystem_upload_chunk_storage_path VARCHAR(500),
    filesystem_upload_chunk_status INT,
    filesystem_upload_chunk_upload_time TIMESTAMP,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_filesystem_upload_chunk_upload_id ON filesystem_upload_chunk(filesystem_upload_chunk_upload_id);

CREATE TABLE IF NOT EXISTS filesystem_upload_file (

    filesystem_upload_file_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filesystem_upload_file_upload_id VARCHAR(64) NOT NULL UNIQUE,
    filesystem_upload_file_name VARCHAR(255),
    filesystem_upload_file_original_name VARCHAR(255),
    filesystem_upload_file_size BIGINT,
    filesystem_upload_file_storage_type VARCHAR(50),
    filesystem_upload_file_bucket VARCHAR(100),
    filesystem_upload_file_key VARCHAR(255),
    filesystem_upload_file_url VARCHAR(500),
    filesystem_upload_file_content_type VARCHAR(100),
    filesystem_upload_file_etag VARCHAR(128),
    filesystem_upload_file_complete_time TIMESTAMP,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50)
);

-- ---------------------------------------------------------------------
-- source: V1.3__create_merge_upload_log_tables.sql
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS filesystem_upload_merge_log (

    filesystem_upload_merge_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filesystem_upload_merge_log_upload_id VARCHAR(64) NOT NULL,
    filesystem_upload_merge_log_stage INT DEFAULT 1,
    filesystem_upload_merge_log_status INT NOT NULL,
    filesystem_upload_merge_log_message VARCHAR(500),
    filesystem_upload_merge_log_retry_count INT DEFAULT 0,
    filesystem_upload_merge_log_start_time TIMESTAMP,
    filesystem_upload_merge_log_end_time TIMESTAMP,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_filesystem_upload_merge_log_upload_id ON filesystem_upload_merge_log(filesystem_upload_merge_log_upload_id);

CREATE TABLE IF NOT EXISTS filesystem_upload_upload_log (

    filesystem_upload_upload_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filesystem_upload_upload_log_upload_id VARCHAR(64) NOT NULL,
    filesystem_upload_upload_log_stage INT DEFAULT 2,
    filesystem_upload_upload_log_status INT NOT NULL,
    filesystem_upload_upload_log_message VARCHAR(500),
    filesystem_upload_upload_log_retry_count INT DEFAULT 0,
    filesystem_upload_upload_log_start_time TIMESTAMP,
    filesystem_upload_upload_log_end_time TIMESTAMP,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_filesystem_upload_upload_log_upload_id ON filesystem_upload_upload_log(filesystem_upload_upload_log_upload_id);
