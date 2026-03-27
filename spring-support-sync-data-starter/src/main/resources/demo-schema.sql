CREATE TABLE IF NOT EXISTS monitor_sync_task (
    sync_task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_name VARCHAR(255) NOT NULL,
    sync_task_desc VARCHAR(500),
    sync_task_status VARCHAR(50) DEFAULT 'STOPPED',
    sync_task_consume_timeout BIGINT DEFAULT 30000,
    sync_task_retry_count INT DEFAULT 3,
    sync_task_retry_interval BIGINT DEFAULT 1000,
    sync_task_cron VARCHAR(100),
    sync_task_sync_interval BIGINT,
    sync_task_ack_enabled INT DEFAULT 1,
    sync_task_transaction_enabled INT DEFAULT 0,
    sync_task_batch_size INT DEFAULT 1000,
    sync_task_layout CLOB,
    sync_task_last_run_time DATETIME,
    sync_task_last_run_status VARCHAR(50),
    sync_task_run_count BIGINT DEFAULT 0,
    sync_task_success_count BIGINT DEFAULT 0,
    sync_task_fail_count BIGINT DEFAULT 0,
    sync_task_transform_config CLOB,
    sync_task_filter_config CLOB,
    sync_task_sync_mode VARCHAR(50),
    sync_task_incremental_field VARCHAR(100),
    sync_task_conflict_strategy VARCHAR(50),
    sync_task_max_memory_mb INT,
    sync_task_thread_pool_size INT,
    sync_task_create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    sync_task_update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitor_sync_node (
    sync_node_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT NOT NULL,
    sync_node_key VARCHAR(100) NOT NULL,
    sync_node_name VARCHAR(255) NOT NULL,
    sync_node_type VARCHAR(50) NOT NULL,
    sync_node_spi_name VARCHAR(255) NOT NULL,
    sync_node_config CLOB,
    sync_node_position VARCHAR(255),
    sync_node_enabled INT DEFAULT 1,
    sync_node_order INT DEFAULT 0,
    sync_node_desc VARCHAR(500),
    sync_node_create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    sync_node_update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitor_sync_connection (
    sync_connection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT NOT NULL,
    source_node_id BIGINT,
    source_node_key VARCHAR(100),
    source_handle VARCHAR(50),
    target_node_id BIGINT,
    target_node_key VARCHAR(100),
    target_handle VARCHAR(50),
    connection_type VARCHAR(50) DEFAULT 'DATA',
    connection_label VARCHAR(100),
    sync_connection_create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitor_sync_task_log (
    sync_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT NOT NULL,
    sync_log_status VARCHAR(50),
    sync_log_trigger_type VARCHAR(50),
    sync_log_read_count BIGINT DEFAULT 0,
    sync_log_write_count BIGINT DEFAULT 0,
    sync_log_success_count BIGINT DEFAULT 0,
    sync_log_fail_count BIGINT DEFAULT 0,
    sync_log_retry_count BIGINT DEFAULT 0,
    sync_log_dead_letter_count BIGINT DEFAULT 0,
    sync_log_filter_count BIGINT DEFAULT 0,
    sync_log_start_time DATETIME,
    sync_log_end_time DATETIME,
    sync_log_cost BIGINT,
    sync_log_avg_process_time DOUBLE,
    sync_log_throughput DOUBLE,
    sync_log_message CLOB,
    sync_log_stack_trace CLOB
);

CREATE TABLE IF NOT EXISTS monitor_sync_task_log_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_log_id BIGINT,
    sync_task_id BIGINT,
    log_level VARCHAR(20),
    log_content CLOB,
    log_phase VARCHAR(50),
    log_progress INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitor_sync_alert (
    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT,
    alert_type VARCHAR(50),
    alert_level VARCHAR(50),
    alert_message CLOB,
    alert_time DATETIME,
    is_resolved INT DEFAULT 0,
    resolved_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitor_sync_statistics (
    stat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT,
    stat_date DATE,
    total_records BIGINT DEFAULT 0,
    success_records BIGINT DEFAULT 0,
    failed_records BIGINT DEFAULT 0,
    avg_throughput DECIMAL(18,2),
    avg_latency DECIMAL(18,2),
    peak_memory_mb INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
