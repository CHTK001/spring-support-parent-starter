-- 助手分组归属：只补可空列与索引，既有助手保持未分组，不回写业务值。
SET @has_role_group_binding := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_role'
      AND COLUMN_NAME = 'sys_ai_role_group_id'
);
SET @role_group_binding_sql := IF(@has_role_group_binding = 0,
    'ALTER TABLE sys_ai_role ADD COLUMN sys_ai_role_group_id INT NULL COMMENT ''助手分组，NULL 为未分组''',
    'SELECT 1');
PREPARE role_group_binding_stmt FROM @role_group_binding_sql;
EXECUTE role_group_binding_stmt;
DEALLOCATE PREPARE role_group_binding_stmt;

SET @has_role_group_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_role'
      AND INDEX_NAME = 'idx_ai_role_group'
);
SET @role_group_index_sql := IF(@has_role_group_index = 0,
    'ALTER TABLE sys_ai_role ADD INDEX idx_ai_role_group (sys_ai_role_group_id)',
    'SELECT 1');
PREPARE role_group_index_stmt FROM @role_group_index_sql;
EXECUTE role_group_index_stmt;
DEALLOCATE PREPARE role_group_index_stmt;
