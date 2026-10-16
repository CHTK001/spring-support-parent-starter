-- V1.17 教师工号登录：给 edu_teacher 补登录口令列
--
-- 背景：此前老师端必须先用系统账号（sys_user）登录、再在老师端里绑定工号；
--       而学生早已能直接用学号登录（edu_student.student_password + StudentCredentialProvider）。
--       本版把教师对齐到同一形态：**工号本身就是登录名**，无需另建系统账号。
--
-- 为什么口令不落明文、也不用裸摘要：同 edu_student 的理由（明文一次泄露即全量曝光；
-- 裸 SHA256 可被预计算表反查）。沿用 HMAC-SHA256 + 每师独立随机盐，算法实现复用
-- StudentPasswordSupport（见该类 javadoc：本仓未引入 BCrypt，且不为此新增安全模块）。
--
-- teacher_no 上已有 UNIQUE KEY edu_teacher_teacher_no，按工号登录的查询走该索引，无需新增索引。
-- 存量教师此处不灌默认口令：摘要必须由应用层生成（每行独立盐，SQL 算不出来），
-- 由后台「学生管理 → 教师 → 重置口令」或新建时的「登录口令」写入。

ALTER TABLE edu_teacher
    ADD COLUMN IF NOT EXISTS teacher_password VARCHAR(64) COMMENT '登录口令摘要 hmacSha256(明文,盐)，非明文';

ALTER TABLE edu_teacher
    ADD COLUMN IF NOT EXISTS teacher_pwd_salt VARCHAR(32) COMMENT '口令盐（随机十六进制，与 teacher_password 一一对应）';
