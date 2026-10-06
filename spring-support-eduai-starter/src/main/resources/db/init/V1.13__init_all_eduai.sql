-- ===========================================
-- 教育 AI 模块增量结构变更（V1.13）
-- ===========================================
-- 变更内容：题目审核工作流补两列 ——
--   question_audit_remark：驳回理由（驳回必填，展示用）
--   question_audit_count：累计审核次数（每次通过/驳回 +1）
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
-- 为什么用 information_schema 判断而不是 ADD COLUMN IF NOT EXISTS：
-- MariaDB 11.8 实测不接受该语法（语句报错、列不会建成），
-- MySQL 虽支持但会告警。用「查列 → 不存在才 ALTER」两步走，
-- 在 MariaDB 与 MySQL 上都成立（样板见 V1.3）。
--
-- 存量说明：不回填、不改旧行。题库表不预置题目数据，
-- 存量（若有）靠采集任务重跑补齐；新列 DEFAULT 兜底。
-- ===========================================

-- ----------------------------
-- 题目：驳回理由
-- ----------------------------
-- question_audit_remark：驳回时必填的理由文本，前端展示给题目作者看；
-- 通过时清空（服务端 audit 专用接口里处理，本脚本只建列）。
SET @has_audit_remark := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_question' AND COLUMN_NAME = 'question_audit_remark'
);
SET @sql_audit_remark := IF(@has_audit_remark > 0,
    'SELECT 1',
    'ALTER TABLE edu_question ADD COLUMN question_audit_remark VARCHAR(500) COMMENT ''驳回理由（驳回必填，通过时清空）'' AFTER question_audit_status');
PREPARE stmt_audit_remark FROM @sql_audit_remark;
EXECUTE stmt_audit_remark;
DEALLOCATE PREPARE stmt_audit_remark;

-- ----------------------------
-- 题目：累计审核次数
-- ----------------------------
-- question_audit_count：每次审核动作（通过/驳回）+1，默认 0；
-- 存量行不补（0 = 从未审过，与 0-待审核语义自洽）。
SET @has_audit_count := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_question' AND COLUMN_NAME = 'question_audit_count'
);
SET @sql_audit_count := IF(@has_audit_count > 0,
    'SELECT 1',
    'ALTER TABLE edu_question ADD COLUMN question_audit_count INT DEFAULT 0 COMMENT ''累计审核次数（每次通过/驳回+1）'' AFTER question_audit_remark');
PREPARE stmt_audit_count FROM @sql_audit_count;
EXECUTE stmt_audit_count;
DEALLOCATE PREPARE stmt_audit_count;
