-- ===========================================
-- 教育 AI 模块增量结构变更（V1.4）
-- ===========================================
-- 变更内容：edu_answer_sheet.answer_sheet_exam_id 由 NOT NULL 放宽为可空。
--
-- 为什么需要：
--   扫描阅卷（POST /v2/edu/marking/scan）支持「任意卷」——外来试卷既没有本系统的
--   试卷记录，也没有绑定考试，因此答题卡落库时 exam_id 天然为空。
--   而该列是 NOT NULL 且无默认值，MyBatis-Plus 生成 INSERT 时会省略 null 字段，
--   于是报 "Field 'answer_sheet_exam_id' doesn't have a default value"，整次阅卷失败。
--
-- 为什么可以放宽：
--   唯一键 edu_answer_sheet_exam_student(exam_id, student_id) 在 MySQL/MariaDB 中
--   对含 NULL 的键**不参与去重**（NULL 互不相等），因此同一学生多次扫描（exam_id 均为空）
--   不会撞唯一键；原有的「同一次考试同一学生一张答题卡」约束在有 exam_id 时照旧生效。
--
-- 为什么独立版本：
--   V1.2 已在多个库执行过（sys_database_version 有记录、checksum 固化），
--   改 V1.2 不会对已执行的库生效，故新增版本做幂等 ALTER。
--
-- 幂等：先查 information_schema 的 IS_NULLABLE，已是 YES 就跳过。
-- <b>不用 MODIFY COLUMN IF NOT EXISTS</b>：MariaDB 11.8 不接受该语法（V1.3 已踩过）。
-- ===========================================

-- ----------------------------
-- 答题卡：考试 ID 允许为空
-- ----------------------------
SET @nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'edu_answer_sheet'
      AND COLUMN_NAME = 'answer_sheet_exam_id'
);
SET @sql_nullable := IF(@nullable = 'YES',
    'SELECT 1',
    'ALTER TABLE edu_answer_sheet MODIFY COLUMN answer_sheet_exam_id BIGINT NULL COMMENT ''考试ID（扫描阅卷未绑定考试时为空）''');
PREPARE stmt_nullable FROM @sql_nullable;
EXECUTE stmt_nullable;
DEALLOCATE PREPARE stmt_nullable;
