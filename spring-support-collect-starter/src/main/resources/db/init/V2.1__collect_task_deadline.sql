-- 任务执行期限使用启动快照，禁止用当前全局超时回收其它实例上的长任务。
-- 历史行保留 NULL，不从可能已经修改的流程 DSL 反推。
SET @collect_deadline_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'collect_task'
      AND COLUMN_NAME = 'collect_task_deadline'
);
SET @collect_deadline_sql := IF(@collect_deadline_column_exists = 0,
    'ALTER TABLE collect_task ADD COLUMN collect_task_deadline DATETIME(6) NULL DEFAULT NULL COMMENT ''本次生效超时加收尾宽限的期限快照''',
    'SELECT 1');
PREPARE collect_deadline_stmt FROM @collect_deadline_sql;
EXECUTE collect_deadline_stmt;
DEALLOCATE PREPARE collect_deadline_stmt;
