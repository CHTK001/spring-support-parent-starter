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

CREATE INDEX IF NOT EXISTS idx_filesystem_upload_merge_log_upload_id
    ON filesystem_upload_merge_log(filesystem_upload_merge_log_upload_id);

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

CREATE INDEX IF NOT EXISTS idx_filesystem_upload_upload_log_upload_id
    ON filesystem_upload_upload_log(filesystem_upload_upload_log_upload_id);
