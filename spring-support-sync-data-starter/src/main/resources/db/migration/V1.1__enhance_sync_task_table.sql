-- 数据同步系统增强 - 表结构优化
-- 作者: System
-- 日期: 2026-03-09
-- 说明: 为monitor_sync_task表添加新字段以支持增强功能

-- 1. 新增字段
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_transform_config TEXT COMMENT '数据转换配置JSON';
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_filter_config TEXT COMMENT '数据过滤配置JSON';
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_sync_mode VARCHAR(20) DEFAULT 'FULL' COMMENT '同步模式: FULL/INCREMENTAL/BIDIRECTIONAL';
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_incremental_field VARCHAR(100) COMMENT '增量同步字段';
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_conflict_strategy VARCHAR(20) DEFAULT 'OVERWRITE' COMMENT '冲突策略: OVERWRITE/SKIP/MERGE';
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_max_memory_mb INT DEFAULT 512 COMMENT '最大内存限制(MB)';
ALTER TABLE monitor_sync_task ADD COLUMN sync_task_thread_pool_size INT DEFAULT 5 COMMENT '线程池大小';

-- 2. 添加索引以提升查询性能
CREATE INDEX idx_sync_task_status ON monitor_sync_task(sync_task_status);
CREATE INDEX idx_sync_task_last_run_time ON monitor_sync_task(sync_task_last_run_time);
