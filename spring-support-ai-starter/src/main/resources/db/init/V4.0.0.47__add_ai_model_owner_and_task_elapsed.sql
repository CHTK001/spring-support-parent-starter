-- =============================================================================
-- V4.0.0.47 存量库兼容：模型清单按账号隔离 + 生成耗时列
-- =============================================================================
--
-- 【本脚本取代 V1.1__add_ai_model_owner_and_task_elapsed.sql】
--   原脚本在 MySQL 上从未成功执行过一次，两个缺陷叠加：
--     1. DDL 用了 ALTER TABLE ... ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT
--        EXISTS / DROP INDEX IF EXISTS —— 这是 **MariaDB 语法，MySQL 不支持**。
--        实测 MySQL 8.4.9 首次执行即报 1064 语法错。
--     2. 版本号 1.1 与 `sys_database_version` 里其它模块的
--        V1.1__add_sys_ai_usage_call_time_index.sql 撞主键
--        （该表主键是 version 且被所有模块共用），
--        连执行记录都写不进去 → 每次启动都重跑一遍，每次都失败。
--
-- 【V1.1 头部注释里的「三条硬约束」是错的，请勿再照抄】
--   它声称存在 MysqlScriptConverter 会把 IF NOT EXISTS 展开成
--   information_schema 守卫 + PREPARE/EXECUTE。实际该类**在 jar 里根本不存在**
--   （utils-support-datasource-starter 的 flyway 包下只有 DerbyScriptConverter
--   与 HiveScriptConverter）。迁移器不做任何方言展开，SQL 原样透传给 MySQL，
--   所以 IF NOT EXISTS 直接就是语法错。同目录下 V1.1 / V1.2 / V1.3 都中了这招。
--
-- 【为什么必须独立成脚本】
--   V1.0__init_all_ai.sql 自 2026-09-30 起已记入 sys_database_version（success=true）。
--   迁移器按「完整文件名」去重，命中即整份跳过。把 ALTER 追加到已发布的 V1.0
--   末尾，在任何存量库上都是永不执行的空操作；而这两列又已写进各自的
--   CREATE TABLE，全新库执行 V1.0 时反而必然报 1060 重复列。
--   独立版本脚本是唯一既能让存量库收敛、又不拖累全新库的位置。
--
-- 【本脚本要修的两组 DDL 及其现状】
--   1. sys_ai_generate_task_elapsed —— 生成耗时（毫秒），任务完成时回写。
--      老库靠 CREATE TABLE IF NOT EXISTS 不会补列，运行期 SELECT 因
--      Unknown column 每个轮询周期报一次。
--   2. sys_ai_model_user_id 及配套唯一键 —— 模型清单按账号隔离，
--      对应 AiModelServiceImpl 的归属守卫。
--
--   在本次修复之前的库上，**归属列存在，但索引与复合唯一键都没建成**：
--   `sys_ai_model` 上只有 PRIMARY 与旧的全局唯一键 uk_sys_ai_model_code。
--   也就是说这个脚本当初想解决的「两账号登记同名模型撞键」问题，一直还在。
--
-- 【为什么全部改用「预处理语句」】
--   迁移器按分号把脚本拆成一条条执行，所以下面这套要拆成
--   SET / PREPARE / EXECUTE / DEALLOCATE 四条 —— 拆开后每条都是合法 SQL，
--   组合起来又能按 information_schema 判断「目标是否已存在」，达到幂等。
--   实测连续执行两遍均成功，列与索引都不会重复创建。
--
-- 【执行顺序不可调换】
--   加列 → 建归属索引 → 删旧全局唯一键 → 建复合唯一键。
--   复合键依赖新列；删旧键必须晚于加列、早于建新键，否则中途失败会留下
--   code 全局唯一约束，per-user 登记同名模型依旧撞键。
-- =============================================================================

-- ---------- 1) 生成耗时列（NULL 表示历史行无耗时数据） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_generate_task'
          AND COLUMN_NAME = 'sys_ai_generate_task_elapsed'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_generate_task` ADD COLUMN `sys_ai_generate_task_elapsed` BIGINT NULL'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2) 模型清单归属列（NULL 表示无主历史行） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND COLUMN_NAME = 'sys_ai_model_user_id'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_user_id` VARCHAR(64) NULL'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 3) 归属列普通索引（供按账号过滤） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND INDEX_NAME = 'sys_ai_model_user_id'),
        'SELECT 1',
        'CREATE INDEX `sys_ai_model_user_id` ON `sys_ai_model` (`sys_ai_model_user_id`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 4) 去掉 model_code 的全局唯一键 ----------
-- 两种命名都要删：V1.1 假设旧键叫 sys_ai_model_sys_ai_model_code，
-- 而存量库里实际存在的是 uk_sys_ai_model_code（该库并非由当前 V1.0 脚本创建）。
-- 只删一个名字的话，另一套库上旧的全局唯一键会留下，跨账号登记照样撞键 ——
-- 而这种失败只在特定库上出现，极难排查。
-- MySQL 唯一索引中 NULL 互不相等，故无主历史行之间不受新复合键约束，符合预期。
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND INDEX_NAME = 'sys_ai_model_sys_ai_model_code'),
        'ALTER TABLE `sys_ai_model` DROP INDEX `sys_ai_model_sys_ai_model_code`',
        'SELECT 1'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND INDEX_NAME = 'uk_sys_ai_model_code'),
        'ALTER TABLE `sys_ai_model` DROP INDEX `uk_sys_ai_model_code`',
        'SELECT 1'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 5) 建立（归属, code）复合唯一键 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND INDEX_NAME = 'sys_ai_model_owner_code'),
        'SELECT 1',
        'CREATE UNIQUE INDEX `sys_ai_model_owner_code` ON `sys_ai_model` (`sys_ai_model_user_id`, `sys_ai_model_code`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;