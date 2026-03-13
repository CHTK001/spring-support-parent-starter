-- 创建同步统计表
-- 作者: System
-- 日期: 2026-03-09

CREATE TABLE monitor_sync_statistics (
    stat_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
    sync_task_id BIGINT NOT NULL COMMENT '任务ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    total_records BIGINT DEFAULT 0 COMMENT '总记录数',
    success_records BIGINT DEFAULT 0 COMMENT '成功记录数',
    failed_records BIGINT DEFAULT 0 COMMENT '失败记录数',
    avg_throughput DECIMAL(10,2) COMMENT '平均吞吐量(条/秒)',
    avg_latency DECIMAL(10,2) COMMENT '平均延迟(毫秒)',
    peak_memory_mb INT COMMENT '峰值内存(MB)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_date (sync_task_id, stat_date)
) COMMENT='同步统计表';
