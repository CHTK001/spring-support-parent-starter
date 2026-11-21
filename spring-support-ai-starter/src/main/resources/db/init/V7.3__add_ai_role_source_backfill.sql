-- =============================================================================
-- V7.3 — 助手列表混入模拟角色修复：sys_ai_role.sys_ai_role_source 存量回填
-- 背景：V7.0__initdata_all_ai.sql 播种的三国模拟 NPC（刘备/关羽/张飞/赵云/
--       诸葛亮/黄月英/曹操/貂蝉/孙权/周瑜 共 10 个）未写 sys_ai_role_source，
--       落库为列默认值 'user'；而助手（角色管理）列表
--       SysAiRoleServiceImpl.list()/listEnabled() 会过滤 source = 'world-sim'，
--       于是这批「世界沙盒固定演员」以用户助手身份混进了助手页。
-- 口径：INITDATA 脚本是 ONCE 语义（库里有业务数据就不再执行），存量库吃不到
--       V7.0 里新增的播种后回填；本脚本按 ADD 脚本扫描（版本表记录，每库执行
--       一次），在下次启动时把种子角色归位为 world-sim。
-- 幂等纪律：information_schema 判表/列存在 + 只回填非 world-sim 行，可反复执行。
-- =============================================================================

SET @has_role_table := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_role'
);
SET @has_role_source := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_role' AND COLUMN_NAME = 'sys_ai_role_source'
);
-- 归属口径与 V7.0 播种一致：只回填管理员（admin）名下的种子角色，避免误伤
-- 其它用户自建的同 code 角色；sys_user 不存在（纯 ai 库）时不加归属条件。
SET @has_user_table := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
);
SET @owner_scope := IF(@has_user_table > 0,
    ' AND user_id = COALESCE((SELECT sys_user_id FROM sys_user WHERE sys_user_username = ''admin'' ORDER BY sys_user_id LIMIT 1), user_id)',
    '');
SET @sql_role_source_backfill := IF(@has_role_table > 0 AND @has_role_source > 0,
    CONCAT('UPDATE sys_ai_role',
           ' SET sys_ai_role_source = ''world-sim''',
           ' WHERE sys_ai_role_source <> ''world-sim''',
           ' AND sys_ai_role_code IN (''liu-bei'', ''guan-yu'', ''zhang-fei'', ''zhao-yun'',',
           ' ''zhuge-liang'', ''huang-yue-ying'', ''cao-cao'', ''diao-chan'', ''sun-quan'', ''zhou-yu'')',
           @owner_scope),
    'SELECT 1');
PREPARE stmt_role_source_backfill FROM @sql_role_source_backfill;
EXECUTE stmt_role_source_backfill;
DEALLOCATE PREPARE stmt_role_source_backfill;
