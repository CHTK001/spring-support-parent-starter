-- ===========================================
-- 教育 AI 模块增量结构变更（V1.3）
-- ===========================================
-- 变更内容：为 edu_student 补齐「学生头像」与「身份证号」两列。
--
-- 为什么需要独立版本：
--   V1.2 已在多个库执行过（sys_database_version 里有记录且 checksum 已固化）。
--   再往 V1.2 里加列，对已执行过的库不会生效——CREATE TABLE IF NOT EXISTS
--   遇到已存在的表会整条跳过，不会补列；而改 V1.2 的内容又会让其 checksum
--   与历史记录失配。两条路都走不通，只能新增版本做幂等 ALTER。
--
-- 全部语句必须幂等：init_all 脚本按版本各跑一次，
-- 但同一脚本也可能在多套环境先后执行，不能假设「只跑一次」。
--
-- <b>为什么用 information_schema 判断而不是 ADD COLUMN IF NOT EXISTS</b>：
-- MariaDB 11.8 实测不接受该语法（语句报错、列不会建成），
-- MySQL 虽支持但会告警。用「查列 → 不存在才 ALTER」两步走，
-- 在 MariaDB 与 MySQL 上都成立。
-- ===========================================

-- ----------------------------
-- 学生档案：补头像与身份证
-- ----------------------------
-- student_avatar：图片 URL 或 Base64，与系统用户头像同口径，前端按 <img src> 直接渲染
SET @has_avatar := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_avatar'
);
SET @sql_avatar := IF(@has_avatar > 0,
    'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_avatar VARCHAR(500) COMMENT ''学生头像（URL 或 Base64，与系统用户头像同口径）'' AFTER student_parent_phone');
PREPARE stmt_avatar FROM @sql_avatar;
EXECUTE stmt_avatar;
DEALLOCATE PREPARE stmt_avatar;

-- student_id_card：敏感字段，仅管理端维护，学生端接口一律不下发
SET @has_id_card := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_id_card'
);
SET @sql_id_card := IF(@has_id_card > 0,
    'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_id_card VARCHAR(32) COMMENT ''身份证号（仅管理端维护，学生端不可见）'' AFTER student_avatar');
PREPARE stmt_id_card FROM @sql_id_card;
EXECUTE stmt_id_card;
DEALLOCATE PREPARE stmt_id_card;
