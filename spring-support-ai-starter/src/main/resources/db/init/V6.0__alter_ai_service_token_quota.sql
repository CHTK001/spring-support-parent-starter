-- =====================================================================
-- V6.0__alter_ai_service_token_quota.sql
-- 通道: ALTER 变更
-- 说明: 服务管理「Key 台账」重构 —— 访问令牌支持绑定模型 + 三种配额方式。
--
--   1) model_ids   允许调用的模型编码（逗号分隔）。为空 = 不限制。
--                  候选来自「模型管理」，鉴权时按 model 等值匹配（忽略大小写）。
--   2) quota_type  配额方式声明：time 按时间 / count 按次数 / balance 按余额（配总量）。
--                  它是「主约束」的声明，前端据此渲染表单与余量尺；
--                  鉴权侧仍对已填写的 expires_at / max_requests / max_tokens 逐条判定，
--                  因此存量行（quota_type 为 NULL）行为不变。
--   3) last_used_at 最近一次成功调用时间，供台账判断「这把 Key 还有人用吗」。
--
--   按余额配额复用既有 max_tokens / used_tokens 两列：余额 = max_tokens - used_tokens，
--   不新增 quota_total / quota_used，避免同一语义存两处、日后互相打架。
--
-- 写法：information_schema 判存 + 动态 DDL。MySQL 8.4 与 MariaDB 11.8 都不支持
--   ADD COLUMN IF NOT EXISTS，本脚本可反复执行，重复运行只走 SELECT 1 分支。
-- =====================================================================

-- ---------- 1. 允许调用的模型编码 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_token'
          AND COLUMN_NAME = 'model_ids'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_token` ADD COLUMN `model_ids` VARCHAR(2048) DEFAULT NULL
             COMMENT ''允许调用的模型编码（逗号分隔），为空表示不限制'' AFTER `token_name`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2. 配额方式 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_token'
          AND COLUMN_NAME = 'quota_type'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_token` ADD COLUMN `quota_type` VARCHAR(16) DEFAULT NULL
             COMMENT ''配额方式：time 按时间 / count 按次数 / balance 按余额'' AFTER `token_type`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 3. 最近一次成功调用时间 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_token'
          AND COLUMN_NAME = 'last_used_at'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_token` ADD COLUMN `last_used_at` DATETIME DEFAULT NULL
             COMMENT ''最近一次成功调用时间'' AFTER `used_tokens`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 4. 存量行回填配额方式 ----------
-- 依据已填写的额度推断主约束，优先级：过期时间 > 请求次数 > 余额 > 永久（time 且不限）。
-- 只填 NULL 行，不覆盖用户新写入的值。
UPDATE `sys_ai_service_token`
   SET `quota_type` = CASE
         WHEN `expires_at` IS NOT NULL THEN 'time'
         WHEN `max_requests` IS NOT NULL THEN 'count'
         WHEN `max_tokens` IS NOT NULL THEN 'balance'
         ELSE 'time'
       END
 WHERE `quota_type` IS NULL OR `quota_type` = '';

-- ---------- 5. 台账按实例 + 配额筛选的辅助索引 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_token'
          AND INDEX_NAME = 'sys_ai_service_token_quota'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_token` ADD KEY `sys_ai_service_token_quota` (`quota_type`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
