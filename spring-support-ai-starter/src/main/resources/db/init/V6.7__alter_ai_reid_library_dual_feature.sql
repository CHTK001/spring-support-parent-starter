-- =============================================================================
-- V6.7 —— 重识别底库改为「一条记录两个特征值」
--
-- 背景：人脸重识别 = 人脸识别管线 + ReID 管线，两条管线用**各自的**特征解释同一张脸。
-- 底库里一条记录要同时供两条管线检索：
--   · 人脸特征（feature_model 产出）→ 人脸识别 1:N，回答「这是谁」；
--   · ReID 特征（reid_feature_model 产出）→ 重识别 1:N，回答「这个人去过哪」。
-- 因此除原有的 sys_ai_reid_library_vector_id（ReID 特征向量）之外，另加一列
-- sys_ai_reid_library_face_vector_id 承载人脸特征向量；两者在入库时一次算完、一次写入，
-- 分属两个向量库（reid / reid-face），互不覆盖。
--
-- 另加 sys_ai_reid_library_match_type：记录该行当初由哪条管线判定入库
-- （FACE = 置信度达标走了人脸识别；REID = 只到 ReID 阈值）。同一张脸只由一条管线回答，
-- 这一列是那个结论的持久形态，检索时据此决定该行主用哪个特征。
-- 历史行没有该值时按 NULL 处理，检索侧按「两列都有就用对应列」兜底，不需要回填。
--
-- 写法与 V5.2__alter_ai_face_reid_geo.sql 一致（幂等审计要求）：
--   查 information_schema → 不存在才执行 ALTER（PREPARE/EXECUTE）。
--   不用 ADD COLUMN IF NOT EXISTS：MariaDB 11.8 实测不接受该语法。
-- 每列/每个索引各自独立一段：MySQL 的 ALTER 是多子句整体生效，
-- 一个子句失败整条都不生效，拆开才能「缺哪补哪」。
-- =============================================================================

-- ---------- sys_ai_reid_library：人脸特征向量标识 ----------
SET @has_reid_face_vec := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_face_vector_id'
);
SET @sql_reid_face_vec := IF(@has_reid_face_vec > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_face_vector_id VARCHAR(96) DEFAULT NULL COMMENT ''人脸特征向量标识（feature_model 产出；与 vector_id 的 ReID 特征分库）''');
PREPARE stmt_reid_face_vec FROM @sql_reid_face_vec;
EXECUTE stmt_reid_face_vec;
DEALLOCATE PREPARE stmt_reid_face_vec;

-- ---------- sys_ai_reid_library：分流类型 ----------
SET @has_reid_match_type := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_match_type'
);
SET @sql_reid_match_type := IF(@has_reid_match_type > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_match_type VARCHAR(16) DEFAULT NULL COMMENT ''入库时的分流结论：FACE=人脸识别 / REID=仅到 ReID 阈值''');
PREPARE stmt_reid_match_type FROM @sql_reid_match_type;
EXECUTE stmt_reid_match_type;
DEALLOCATE PREPARE stmt_reid_match_type;

-- ---------- sys_ai_reid_library：人脸向量标识索引 ----------
-- 按人脸特征回查底库行时用得上（人脸识别命中 → 反查底库档案）。
SET @has_reid_face_vec_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND INDEX_NAME = 'sys_ai_reid_library_face_vector'
);
SET @sql_reid_face_vec_idx := IF(@has_reid_face_vec_idx > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD KEY sys_ai_reid_library_face_vector (sys_ai_reid_library_user_id, sys_ai_reid_library_face_vector_id)');
PREPARE stmt_reid_face_vec_idx FROM @sql_reid_face_vec_idx;
EXECUTE stmt_reid_face_vec_idx;
DEALLOCATE PREPARE stmt_reid_face_vec_idx;
