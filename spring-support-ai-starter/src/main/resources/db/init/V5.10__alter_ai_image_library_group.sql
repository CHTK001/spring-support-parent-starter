-- =============================================================================
-- V5.10 —— 以图搜图底库的分组隔离（补列 / 放宽唯一键 / 加大组索引）
--
-- 背景：
--   `sys_ai_image_library` 在建表（V5.1__init_all_ai.sql）时还没有分组列，
--   存量行的 `group` 是 NULL，语义上归属默认组 `default`。
--   更关键的是，旧唯一键是（user_id, vector_id），只有用户和向量两个口径——
--   到「每个账号 + 分组各自成立一份底库」这一步，约束必须加上分组：
--   同一个账号、同一个分组里，同一张图片只能登记一次。
--
-- 三件事：
--   1. 补列 sys_ai_image_library_group VARCHAR(64) DEFAULT 'default'，
--      存量 NULL 行自动归口到 `default`。
--   2. 拆（user_id, vector_id）唯一键，建（user_id, vector_id, group）：
--      历史行里 user_id=NULL 的重复（vector_id, group）在拆前收口，
--      否则新键一建就报 Duplicate entry，这版补丁一次都执行不完。
--   3. 加组索引，让 listGroups / 按组分页的查询只扫组。
--
-- 写法：information_schema 判存 + PREPARE/EXECUTE，与
--   V5.2__alter_ai_face_reid_geo.sql 口径一致（可反复执行，重复运行只走
--   SELECT 1 分支）。
--
-- 注意：这是 NORMAL 补丁脚本，INIT 阶段建表的 V5.1__init_all_ai.sql 先于
--   它执行，所以 `sys_ai_image_library` 一定已存在。
-- =============================================================================

-- ---------- 1. 补列：sys_ai_image_library_group ----------
SET @has_image_group := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
      AND COLUMN_NAME = 'sys_ai_image_library_group'
);
SET @sql_image_group := IF(@has_image_group > 0, 'SELECT 1',
    'ALTER TABLE `sys_ai_image_library` ADD COLUMN `sys_ai_image_library_group` VARCHAR(64) DEFAULT ''default'' COMMENT ''分组（group）''');
PREPARE st FROM @sql_image_group;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ---------- 2. 历史共迁：user_id=NULL 的重复（vector_id, group）只留 id 最小的一条 ----------
-- 旧唯一键（user_id, vector_id）允许 user_id=NULL 时同 vector_id 重复出现，
-- 新键（user_id, vector_id, group）下它们全部成药 default，直接升级唯一键会
-- 1062（Duplicate entry）。这些行没有归属，重复入库，留下最小 id 的那条即可。
SET @image_dup_groups := (
    SELECT COUNT(*) FROM (
        SELECT 1 FROM sys_ai_image_library
        WHERE user_id IS NULL
        GROUP BY vector_id, group
        HAVING COUNT(*) > 1
    ) AS keep
);
SET @sql_image_dup := IF(@image_dup_groups > 0, CONCAT(
    'DELETE t FROM sys_ai_image_library t JOIN sys_ai_image_library x ',
    'ON x.user_id IS NULL AND t.user_id IS NULL ',
    'AND x.sys_ai_image_library_vector_id = t.sys_ai_image_library_vector_id ',
    'AND x.sys_ai_image_library_group = t.sys_ai_image_library_group ',
    'AND x.sys_ai_image_library_id < t.sys_ai_image_library_id'),
    'SELECT 1');
PREPARE st FROM @sql_image_dup;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ---------- 3. 拆旧唯一键（（user_id, vector_id），2 列） ----------
-- 只拆 2 列的：3 列的已经是新口径，0 列的说明从未建过。
SET @image_owner_rows := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
      AND INDEX_NAME = 'sys_ai_image_library_owner_vector'
);
SET @sql_image_drop := IF(@image_owner_rows = 2,
    'ALTER TABLE `sys_ai_image_library` DROP INDEX `sys_ai_image_library_owner_vector`',
    'SELECT 1');
PREPARE st FROM @sql_image_drop;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ---------- 4. 建（user_id, vector_id, group）唯一键 ----------
SET @image_owner := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
      AND INDEX_NAME = 'sys_ai_image_library_owner_vector'
);
SET @sql_image_owner := IF(@image_owner >= 3, 'SELECT 1',
    'CREATE UNIQUE INDEX `sys_ai_image_library_owner_vector` ON `sys_ai_image_library` ('
    || '`sys_ai_image_library_user_id`, `sys_ai_image_library_vector_id`, `sys_ai_image_library_group`)');
PREPARE st FROM @sql_image_owner;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ---------- 5. 加组索引（只扫组） ----------
SET @image_group_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
      AND INDEX_NAME = 'sys_ai_image_library_image_group'
);
SET @sql_image_group_idx := IF(@image_group_idx > 0, 'SELECT 1',
    'CREATE INDEX `sys_ai_image_library_image_group` ON `sys_ai_image_library` (`sys_ai_image_library_group`)');
PREPARE st FROM @sql_image_group_idx;
EXECUTE st;
DEALLOCATE PREPARE st;
