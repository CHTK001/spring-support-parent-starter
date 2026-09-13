-- 幂等：仅当 filesystem_upload_task 表缺 stage 列时才新增（MySQL 不支持 ADD COLUMN IF NOT EXISTS）
SET @fs_col_exists = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='filesystem_upload_task' AND column_name='filesystem_upload_task_stage'),
    'SELECT 1',
    'ALTER TABLE filesystem_upload_task ADD COLUMN filesystem_upload_task_stage INT DEFAULT 0'
);
PREPARE fs_stmt FROM @fs_col_exists; EXECUTE fs_stmt; DEALLOCATE PREPARE fs_stmt;
