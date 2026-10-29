-- =============================================================================
-- V5.3 —— 补齐「老库建表时还不存在」的列（sys_ai_role 可见性）
--
-- 背景：V5.1 建表语句 CREATE TABLE IF NOT EXISTS sys_ai_role 不含
--       sys_ai_role_visibility 列，但 4.0.0.42 起实体 SysAiRole 已新增
--       visibility 字段（映射列 sys_ai_role_visibility），用于「仅管理员可见」
--       的内置审计角色（prompt-auditor）。老库缺列会导致：
--         1) 市场「添加」审计角色时 INSERT ... visibility=2 报 Unknown column
--         2) 列表/下拉/详情 SELECT 读 visibility 失败
--
-- 写法与 V5.2 一致（幂等审计要求）：查 information_schema → 不存在才执行
--   ALTER（PREPARE/EXECUTE），不用 ADD COLUMN IF NOT EXISTS（MariaDB 11.8
--   实测不接受该语法）。
--
-- 列类型与 V5.1 建表定义对齐：INT DEFAULT 1 COMMENT。
-- =============================================================================

SET @has_role_visibility := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_role' AND COLUMN_NAME = 'sys_ai_role_visibility'
);
SET @sql_role_visibility := IF(@has_role_visibility > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_role ADD COLUMN sys_ai_role_visibility INT DEFAULT 1 COMMENT ''1 普通用户可见；2 仅管理员可见（如内置审计角色）''');
PREPARE stmt_role_visibility FROM @sql_role_visibility;
EXECUTE stmt_role_visibility;
DEALLOCATE PREPARE stmt_role_visibility;
