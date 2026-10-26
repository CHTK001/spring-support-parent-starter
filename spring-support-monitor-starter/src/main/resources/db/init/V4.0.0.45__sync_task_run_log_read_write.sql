-- ============================================================
-- 同步执行日志：读/写行数结构化列（补丁脚本）
--
-- 承接 V4.0.0.44__sync_task_run_log.sql。目的：同步日志列表页
-- 要展示「读 N 行 / 写 N 行」，又不希望在列表场景把 LONGTEXT 正文
-- 一并查出来（列表只需行数标题，正文在点开详情时才整体读回）。
-- 这里加两个小列，由 SyncTaskRunLogBuffer.persist() 在执行结束时
-- 从过程日志解析后写入；存量行读/写为 NULL，详情页仍会从正文动态
-- 解析作为兜底，因此旧数据在详情弹窗里依然能看到读/写行数。
--
-- NORMAL 脚本（文件名不含 init）：按自身 checksum 记账，首次启动执行一次。
-- ============================================================
ALTER TABLE `ms_sync_task_run_log`
    ADD COLUMN `ms_sync_task_run_log_read_count`  BIGINT DEFAULT NULL COMMENT '本轮读取行数（执行结束时从过程日志解析）' AFTER `ms_sync_task_run_log_line_count`,
    ADD COLUMN `ms_sync_task_run_log_write_count` BIGINT DEFAULT NULL COMMENT '本轮写入行数（执行结束时从过程日志解析）' AFTER `ms_sync_task_run_log_read_count`;