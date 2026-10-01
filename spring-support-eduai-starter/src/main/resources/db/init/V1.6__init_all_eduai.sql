-- ===========================================
-- 教育 AI 模块增量结构变更（V1.6）
-- ===========================================
-- 变更内容：edu_marking_task.marking_task_image_path 由 VARCHAR(512) 扩到 VARCHAR(2000)。
--
-- 为什么需要：
--   扫描阅卷支持「一份试卷多张图片」（试卷通常跨多页），任务表把多张图片的落盘路径
--   按页序以逗号连接存在这一列。原 512 字符按每路径约 60 字符估算只能放 ~8 页，
--   而单次扫描允许最多 20 张（EduMarkingServiceImpl.MAX_SCAN_IMAGES），会被静默截断，
--   续跑时读不到后面的页。
--
-- 为什么独立版本：
--   V1.5 已在库中执行、checksum 已固化，改 V1.5 不会对已执行的库生效，故新增版本做幂等 ALTER。
--
-- 幂等：先查 information_schema 的 CHARACTER_MAXIMUM_LENGTH，已达 2000 就跳过。
-- 不用 MODIFY COLUMN IF NOT EXISTS：MariaDB 11.8 不接受该语法（V1.3 已踩过）。
-- ===========================================

SET @img_len := (
    SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'edu_marking_task'
      AND COLUMN_NAME = 'marking_task_image_path'
);
SET @sql_img_len := IF(@img_len IS NULL OR @img_len >= 2000,
    'SELECT 1',
    'ALTER TABLE edu_marking_task MODIFY COLUMN marking_task_image_path VARCHAR(2000) COMMENT ''上传图片的落盘路径（多页以逗号按页序连接，续跑时读取）''');
PREPARE stmt_img_len FROM @sql_img_len;
EXECUTE stmt_img_len;
DEALLOCATE PREPARE stmt_img_len;
