-- =============================================================================
-- V5.2 —— 补齐「老库建表时还不存在」的列（人脸底库 / ReID 底库）
--
-- 背景：db/init 下的脚本以版本记录（sys_database_version）为界只跑一次，
-- 而建表语句一律是 CREATE TABLE IF NOT EXISTS —— 表已存在时，V5.1 之后
-- 新增的列永远不会补进老库。运行期表现：
--   java.sql.SQLSyntaxErrorException: Unknown column 'sys_ai_face_library_lat' in 'field list'
--   FaceLibraryLinkService: [Face] 读取底库档案失败
-- ReID 底库同样缺列（user_id / track_id / camera_id / lat / lng），
-- /v2/reid/library、/v2/reid/tracks 整条查询失败。
--
-- 写法与 V2.1__init_all_eduai.sql 末尾那批补列保持一致（幂等审计要求）：
--   查 information_schema → 不存在才执行 ALTER（PREPARE/EXECUTE）。
--   不用 ADD COLUMN IF NOT EXISTS：MariaDB 11.8 实测不接受该语法。
--
-- 每列/每个索引各自独立一段（不是一条 ALTER 带多个子句）：MySQL 的 ALTER
-- 是多子句整体生效，一个子句失败整条都不生效，拆开才能「缺哪补哪」。
--
-- 列类型与注释对齐 V5.1__init_all_ai.sql 的建表定义。
-- =============================================================================

-- ---------- sys_ai_face_library：地理坐标 ----------
SET @has_face_lat := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library' AND COLUMN_NAME = 'sys_ai_face_library_lat'
);
SET @sql_face_lat := IF(@has_face_lat > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_face_library ADD COLUMN sys_ai_face_library_lat DECIMAL(10,7) DEFAULT NULL COMMENT ''纬度（非必填，WGS84，-90~90）''');
PREPARE stmt_face_lat FROM @sql_face_lat;
EXECUTE stmt_face_lat;
DEALLOCATE PREPARE stmt_face_lat;

SET @has_face_lng := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library' AND COLUMN_NAME = 'sys_ai_face_library_lng'
);
SET @sql_face_lng := IF(@has_face_lng > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_face_library ADD COLUMN sys_ai_face_library_lng DECIMAL(10,7) DEFAULT NULL COMMENT ''经度（非必填，WGS84，-180~180）''');
PREPARE stmt_face_lng FROM @sql_face_lng;
EXECUTE stmt_face_lng;
DEALLOCATE PREPARE stmt_face_lng;

SET @has_face_geo := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library' AND INDEX_NAME = 'sys_ai_face_library_geo'
);
SET @sql_face_geo := IF(@has_face_geo > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_face_library ADD KEY sys_ai_face_library_geo (sys_ai_face_library_lat, sys_ai_face_library_lng)');
PREPARE stmt_face_geo FROM @sql_face_geo;
EXECUTE stmt_face_geo;
DEALLOCATE PREPARE stmt_face_geo;

-- ---------- sys_ai_reid_library：账号隔离 / 轨迹 / 摄像头 / 地理坐标 ----------
SET @has_reid_user := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_user_id'
);
SET @sql_reid_user := IF(@has_reid_user > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_user_id VARCHAR(64) DEFAULT NULL COMMENT ''归属账号（按账号隔离；NULL=无主历史行）''');
PREPARE stmt_reid_user FROM @sql_reid_user;
EXECUTE stmt_reid_user;
DEALLOCATE PREPARE stmt_reid_user;

SET @has_reid_track := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_track_id'
);
SET @sql_reid_track := IF(@has_reid_track > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_track_id VARCHAR(64) DEFAULT NULL COMMENT ''跨帧轨迹ID - 同一条轨迹的多条观测共享''');
PREPARE stmt_reid_track FROM @sql_reid_track;
EXECUTE stmt_reid_track;
DEALLOCATE PREPARE stmt_reid_track;

SET @has_reid_cam := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_camera_id'
);
SET @sql_reid_cam := IF(@has_reid_cam > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_camera_id VARCHAR(128) DEFAULT NULL COMMENT ''摄像头标识（跨镜判定依据）''');
PREPARE stmt_reid_cam FROM @sql_reid_cam;
EXECUTE stmt_reid_cam;
DEALLOCATE PREPARE stmt_reid_cam;

SET @has_reid_lat := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_lat'
);
SET @sql_reid_lat := IF(@has_reid_lat > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_lat DECIMAL(10,7) DEFAULT NULL COMMENT ''纬度（非必填，WGS84，-90~90）''');
PREPARE stmt_reid_lat FROM @sql_reid_lat;
EXECUTE stmt_reid_lat;
DEALLOCATE PREPARE stmt_reid_lat;

SET @has_reid_lng := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND COLUMN_NAME = 'sys_ai_reid_library_lng'
);
SET @sql_reid_lng := IF(@has_reid_lng > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD COLUMN sys_ai_reid_library_lng DECIMAL(10,7) DEFAULT NULL COMMENT ''经度（非必填，WGS84，-180~180）''');
PREPARE stmt_reid_lng FROM @sql_reid_lng;
EXECUTE stmt_reid_lng;
DEALLOCATE PREPARE stmt_reid_lng;

SET @has_reid_owner_vec := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND INDEX_NAME = 'sys_ai_reid_library_owner_vector'
);
SET @sql_reid_owner_vec := IF(@has_reid_owner_vec > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD UNIQUE KEY sys_ai_reid_library_owner_vector (sys_ai_reid_library_user_id, sys_ai_reid_library_vector_id)');
PREPARE stmt_reid_owner_vec FROM @sql_reid_owner_vec;
EXECUTE stmt_reid_owner_vec;
DEALLOCATE PREPARE stmt_reid_owner_vec;

SET @has_reid_user_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND INDEX_NAME = 'sys_ai_reid_library_user'
);
SET @sql_reid_user_idx := IF(@has_reid_user_idx > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD KEY sys_ai_reid_library_user (sys_ai_reid_library_user_id)');
PREPARE stmt_reid_user_idx FROM @sql_reid_user_idx;
EXECUTE stmt_reid_user_idx;
DEALLOCATE PREPARE stmt_reid_user_idx;

SET @has_reid_geo := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_reid_library' AND INDEX_NAME = 'sys_ai_reid_library_geo'
);
SET @sql_reid_geo := IF(@has_reid_geo > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_reid_library ADD KEY sys_ai_reid_library_geo (sys_ai_reid_library_lat, sys_ai_reid_library_lng)');
PREPARE stmt_reid_geo FROM @sql_reid_geo;
EXECUTE stmt_reid_geo;
DEALLOCATE PREPARE stmt_reid_geo;
