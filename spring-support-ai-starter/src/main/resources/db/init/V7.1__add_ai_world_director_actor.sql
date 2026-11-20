-- =============================================================================
-- V7.1 — 模拟（世界沙盒）：sys_ai_world 补 director_model / actor_roles 两列
-- 背景：实体于 2026-11-11（commit 78659d115）引入「导演模型」与「演员/角色」
--       两个字段，但 V7.0 建表与全部历史 DDL 均未补列；已建库缺列导致
--       world-sim 系列查询直接 Unknown column 500（"数据操作失败"）。
-- 幂等纪律：PREPARE/EXECUTE + information_schema.COLUMNS 判存在，可反复执行。
-- =============================================================================

SET @has_world_director_model := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world' AND COLUMN_NAME = 'director_model'
);
SET @sql_world_director_model := IF(@has_world_director_model > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world ADD COLUMN director_model VARCHAR(128) DEFAULT NULL COMMENT ''导演模型：世界的剧本导演用哪个模型写剧本（可空，沿用全局默认）''');
PREPARE stmt_world_director_model FROM @sql_world_director_model;
EXECUTE stmt_world_director_model;
DEALLOCATE PREPARE stmt_world_director_model;

SET @has_world_actor_roles := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world' AND COLUMN_NAME = 'actor_roles'
);
SET @sql_world_actor_roles := IF(@has_world_actor_roles > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world ADD COLUMN actor_roles TEXT DEFAULT NULL COMMENT ''演员/角色配置：每行一个角色名（纯文本，例：「诸葛亮\n曹操」），可空''');
PREPARE stmt_world_actor_roles FROM @sql_world_actor_roles;
EXECUTE stmt_world_actor_roles;
DEALLOCATE PREPARE stmt_world_actor_roles;