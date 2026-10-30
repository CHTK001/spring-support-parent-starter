-- =============================================================================
-- V5.6 —— 模拟（AI 剧本模拟）：幕次系统 + NPC 属性/状态扩展
--
-- 依赖：V5.4 六张表已建，V5.5 三国演义种子已灌。
--
-- 1) sys_ai_world_act（新表）— 剧本模拟的幕次元数据。完整剧本（场景/台词/道具/
--    过场/媒体句柄）落盘服务器目录：{story_base_path}/{worldId}/acts/act{N}.json
--    （按世界隔离；用户侧观看进度/故事片段存 {story_base_path}/{worldId}/stories/{userId}/...）。
-- 2) sys_ai_world_npc（补列）— age/ageEnabled/status/gender/profession，
--    为「年龄增长、生老病死、职业身份」三类模拟提供数据。
--
-- 幂等纪律（重要）：
--   建表    CREATE TABLE IF NOT EXISTS
--   补列    PREPARE/EXECUTE 查 information_schema，存在则跳过（MariaDB 不接受
--           ADD COLUMN IF NOT EXISTS）；每条 ALTER 独立一段，休要一条多子句整体生成。
--   索引    同样先查 STATISTICS 再决定是否建。
--
-- 全部语句可反复执行，不会产生重复行/重复列/重复索引。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1) sys_ai_world_act：幕次元数据
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world_act` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `world_id` BIGINT NOT NULL COMMENT '世界ID',
  `act_no` INT NOT NULL COMMENT '幕次编号（从 1 开始连续递增）',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '幕标题',
  `description` TEXT COMMENT '本幕概要（演员/场景/悬念，演员表来自剧本文件）',
  `status` VARCHAR(32) NOT NULL DEFAULT 'writing' COMMENT '状态：writing/props/playing/replay/finished/aborted',
  `stage` VARCHAR(32) NOT NULL DEFAULT 'writing' COMMENT '进度阶段：writing/props/playing/replay',
  `progress` INT DEFAULT 0 COMMENT '演出进度（当前场景编号，0=未开始）',
  `ended` TINYINT(1) DEFAULT 0 COMMENT '是否已大结局',
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_world_act` (`world_id`, `act_no`),
  KEY `idx_wa_world_status` (`world_id`, `status`, `updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI剧本模拟-幕次（完整剧本落盘文件）';

-- ---------------------------------------------------------------------
-- 2) sys_ai_world_npc：年龄 / 状态 / 性别 / 职业
-- ---------------------------------------------------------------------

SET @has_npc_age := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world_npc' AND COLUMN_NAME = 'age'
);
SET @sql_npc_age := IF(@has_npc_age > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world_npc ADD COLUMN age INT DEFAULT 0 COMMENT ''年龄（虚岁）''');
PREPARE stmt_npc_age FROM @sql_npc_age;
EXECUTE stmt_npc_age;
DEALLOCATE PREPARE stmt_npc_age;

SET @has_npc_age_ENABLED := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world_npc' AND COLUMN_NAME = 'age_enabled'
);
SET @sql_npc_age_enabled := IF(@has_npc_age_ENABLED > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world_npc ADD COLUMN age_enabled TINYINT(1) DEFAULT 1 COMMENT ''年龄开关：1=随时间增长，0=冻结''');
PREPARE stmt_npc_age_enabled FROM @sql_npc_age_enabled;
EXECUTE stmt_npc_age_enabled;
DEALLOCATE PREPARE stmt_npc_age_enabled;

SET @has_npc_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world_npc' AND COLUMN_NAME = 'status'
);
SET @sql_npc_status := IF(@has_npc_status > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world_npc ADD COLUMN status VARCHAR(16) DEFAULT ''alive'' COMMENT ''NPC状态：alive/dead/birth/away''');
PREPARE stmt_npc_status FROM @sql_npc_status;
EXECUTE stmt_npc_status;
DEALLOCATE PREPARE stmt_npc_status;

SET @has_npc_gender := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world_npc' AND COLUMN_NAME = 'gender'
);
SET @sql_npc_gender := IF(@has_npc_gender > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world_npc ADD COLUMN gender VARCHAR(8) DEFAULT NULL COMMENT ''性别：male/female/unknown''');
PREPARE stmt_npc_gender FROM @sql_npc_gender;
EXECUTE stmt_npc_gender;
DEALLOCATE PREPARE stmt_npc_gender;

SET @has_npc_profession := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world_npc' AND COLUMN_NAME = 'profession'
);
SET @sql_npc_profession := IF(@has_npc_profession > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world_npc ADD COLUMN profession VARCHAR(64) DEFAULT NULL COMMENT ''职业/身份（如 丞相/将军/侍从/学者）''');
PREPARE stmt_npc_profession FROM @sql_npc_profession;
EXECUTE stmt_npc_profession;
DEALLOCATE PREPARE stmt_npc_profession;

SET @has_npc_age_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_world_npc' AND INDEX_NAME = 'idx_npc_age'
);
SET @sql_npc_age_idx := IF(@has_npc_age_idx > 0, 'SELECT 1',
    'ALTER TABLE sys_ai_world_npc ADD KEY idx_npc_age (age_enabled, age, status)');
PREPARE stmt_npc_age_idx FROM @sql_npc_age_idx;
EXECUTE stmt_npc_age_idx;
DEALLOCATE PREPARE stmt_npc_age_idx;

-- ---------------------------------------------------------------------
-- 3) 三国演义 NPC 角色属性种子（年龄/性别/职业/状态）
-- ---------------------------------------------------------------------
-- 注意（重要）：这三列是角色设定，不依赖 LLM 生成，故随 V5.6 与 DDL 同版一次写好。
-- UPDATE 本身幂等：用 (world_id, role_id) 匹配已生成的 NPC，只写对应行，执行多次不覆盖。
UPDATE sys_ai_world_npc n
LEFT JOIN sys_ai_role r ON r.sys_ai_role_id = n.role_id
SET n.age = CASE r.sys_ai_role_name
                 WHEN '曹操' THEN 55
                 WHEN '刘备' THEN 54
                 WHEN '关羽' THEN 52
                 WHEN '张飞' THEN 50
                 WHEN '赵云' THEN 24
                 WHEN '诸葛亮' THEN 48
                 WHEN '黄月英' THEN 24
                 WHEN '周瑜' THEN 36
                 WHEN '孙权' THEN 30
                 WHEN '貂蝉' THEN 21
                 WHEN '吕布' THEN 32
                 WHEN '司马懿' THEN 45
                 ELSE 0
             END,
    n.age_enabled = 1,
    n.status = 'alive',
    n.gender = CASE r.sys_ai_role_name WHEN '貂蝉' THEN 'female' ELSE 'male' END,
    n.profession = CASE r.sys_ai_role_name
                 WHEN '曹操' THEN '丞相/魏'
                 WHEN '刘备' THEN '诸侯/蜀'
                 WHEN '关羽' THEN '将军/蜀'
                 WHEN '张飞' THEN '将军/蜀'
                 WHEN '诸葛亮' THEN '军师/蜀'
                 WHEN '周瑜' THEN '都督/吴'
                 WHEN '孙权' THEN '诸侯/吴'
                 WHEN '貂蝉' THEN '歌姬/美人'
                 WHEN '吕布' THEN '武将/群雄'
                 WHEN '司马懿' THEN '太尉/魏'
                 ELSE 'NPC'
             END
WHERE n.world_id = 1
  AND r.sys_ai_role_name IS NOT NULL;


