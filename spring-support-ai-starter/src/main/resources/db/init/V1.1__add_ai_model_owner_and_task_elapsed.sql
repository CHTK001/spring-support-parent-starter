-- =============================================================================
-- V1.1 存量库兼容：模型清单按账号隔离 + 生成耗时列
-- =============================================================================
--
-- 【为什么必须独立成脚本】
--   V1.0__init_all_ai.sql 自 2026-09-30 起已记入 sys_database_version（success=true）。
--   DataSourceFlyway 按「完整文件名」去重（applied.get(script.fileName())），
--   命中即整份跳过。因此把 ALTER 追加到已发布的 V1.0 末尾，在任何存量库上都是
--   永不执行的空操作；而这两列又已写进各自的 CREATE TABLE，全新库执行 V1.0 时
--   反而必然报 1060 重复列，把整份脚本标记为 success=false 并每次启动重试。
--   独立版本脚本是唯一既能让存量库收敛、又不拖累全新库的位置。
--
-- 【本脚本搬走的两组 DDL 及缘由】
--   1. sys_ai_generate_task_elapsed —— 生成耗时（毫秒），任务完成时回写。
--      该列原先在建表之后才加入，老库靠 CREATE TABLE IF NOT EXISTS 不会补列，
--      运行期 SELECT 因 Unknown column 每个轮询周期报一次。
--   2. sys_ai_model_user_id 及配套唯一键 —— 模型清单按账号隔离，
--      对应 AiModelServiceImpl 的归属守卫。原先 sys_ai_model 无归属列、清单全局，
--      而 /v2/models 六个端点整类限 admin，前端侧边栏「模型管理」却对所有账号可见，
--      普通账号点进去必然 403。唯一键必须一并从「model_code 全局唯一」改成
--      「（归属, code）复合唯一」，否则两个账号都想登记同名模型时会撞唯一键，
--      per-user 直接不可用。（MySQL 唯一索引中 NULL 互不相等，故无主历史行之间
--      不受约束，符合预期。）
--
-- 【三条硬约束 —— 违反其一即失效，务必保持】
--   1. 必须带 IF NOT EXISTS / IF EXISTS。MysqlScriptConverter.expandAddColumn 的
--      首个条件就是 IF NOT EXISTS 分组非空，不带则不展开，原样透传给 MySQL 即语法错。
--   2. ADD COLUMN 的列定义里不能出现单引号。列注释形如 COMMENT 「...」自带引号，
--      会让转换器判定 definition 含引号而放弃展开。故本脚本一律不写 COMMENT，
--      列语义由本文件注释说明。
--   3. 只能使用 ALTER TABLE ... ADD COLUMN、CREATE [UNIQUE] INDEX、
--      DROP INDEX ... ON 三种形式。ALTER TABLE ... ADD KEY 不在转换器的正则内，
--      写了不会被展开，MySQL 上直接语法错。
--
-- 【方言转换来源】utils-support-flyway-starter 的 MysqlScriptConverter
--   （@Spi("mysql")，MYSQL_FAMILY 覆盖 mysql/mariadb/tidb/oceanbase）把上述三种
--   幂等 DDL 展开为 information_schema 守卫 + PREPARE/EXECUTE 动态 SQL，
--   对象已存在时退化为 DO 0 空操作，故本脚本在 MySQL 与 MariaDB 上均可重复执行。
--
-- 【执行顺序不可调换】
--   先加列，再建普通索引，再删旧唯一键，最后建复合唯一键：复合唯一键依赖新列；
--   删旧键必须晚于建新列、早于建新键，否则中途失败会留下 code 全局唯一约束，
--   per-user 登记同名模型依旧撞键。
-- =============================================================================

-- 1) 生成耗时列（NULL 表示历史行无耗时数据）
ALTER TABLE `sys_ai_generate_task`
    ADD COLUMN IF NOT EXISTS `sys_ai_generate_task_elapsed` BIGINT NULL;

-- 2) 模型清单归属列（NULL 表示无主历史行）
ALTER TABLE `sys_ai_model`
    ADD COLUMN IF NOT EXISTS `sys_ai_model_user_id` VARCHAR(64) NULL;

-- 3) 归属列普通索引（供按账号过滤）
CREATE INDEX IF NOT EXISTS `sys_ai_model_user_id` ON `sys_ai_model` (`sys_ai_model_user_id`);

-- 4) 去掉旧的 model_code 全局唯一键
DROP INDEX IF EXISTS `sys_ai_model_sys_ai_model_code` ON `sys_ai_model`;

-- 5) 建立（归属, code）复合唯一键
CREATE UNIQUE INDEX IF NOT EXISTS `sys_ai_model_owner_code` ON `sys_ai_model` (`sys_ai_model_user_id`, `sys_ai_model_code`);