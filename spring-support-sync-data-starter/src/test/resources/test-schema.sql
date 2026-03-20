-- 同步任务表
CREATE TABLE IF NOT EXISTS monitor_sync_task (
    sync_task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_name VARCHAR(255) NOT NULL,
    sync_task_description VARCHAR(500),
    sync_task_status VARCHAR(50) DEFAULT 'STOPPED',
    sync_task_cron VARCHAR(100),
    sync_task_sync_interval BIGINT,
    sync_task_batch_size INT DEFAULT 1000,
    sync_task_last_run_time DATETIME,
    sync_task_last_run_status VARCHAR(50),
    sync_task_run_count INT DEFAULT 0,
    sync_task_success_count INT DEFAULT 0,
    sync_task_fail_count INT DEFAULT 0,
    sync_task_transform_config TEXT,
    sync_task_filter_config TEXT,
    sync_task_sync_mode VARCHAR(50),
    sync_task_incremental_field VARCHAR(100),
    sync_task_conflict_strategy VARCHAR(50),
    sync_task_max_memory_mb INT,
    sync_task_thread_pool_size INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 同步节点表
CREATE TABLE IF NOT EXISTS monitor_sync_node (
    sync_node_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT NOT NULL,
    sync_node_key VARCHAR(100) NOT NULL,
    sync_node_name VARCHAR(255) NOT NULL,
    sync_node_type VARCHAR(50) NOT NULL,
    sync_node_spi_name VARCHAR(255) NOT NULL,
    sync_node_config TEXT,
    sync_node_enabled INT DEFAULT 1,
    sync_node_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 同步任务日志表
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
    sync_log_message TEXT,
    sync_log_stack_trace TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 同步告警表
CREATE TABLE IF NOT EXISTS monitor_sync_alert (
    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT,
    alert_type VARCHAR(50),
    alert_level VARCHAR(50),
    alert_message TEXT,
    alert_time DATETIME,
    is_resolved INT DEFAULT 0,
    resolved_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 同步统计表
CREATE TABLE IF NOT EXISTS monitor_sync_statistics (
    stat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_task_id BIGINT,
    stat_date DATE,
    stat_hour INT,
    total_executions INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    total_read_count BIGINT DEFAULT 0,
    total_write_count BIGINT DEFAULT 0,
    avg_throughput DOUBLE,
    avg_cost BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
