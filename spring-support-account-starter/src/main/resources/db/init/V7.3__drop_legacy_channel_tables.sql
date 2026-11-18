-- ---------------------------------------------------------------------------
-- 渠道管理整体归还账号模块后，清理历史遗留的旧渠道表
--
-- 背景：渠道管理原由 ai-starter 自行实现并建表：
--   sys_ai_provider / sys_ai_provider_model / sys_ai_provider_connection / sys_ai_channel_health
-- 渠道迁移到 account-starter 后（见 V7.2__migrate_channel_to_account.sql），
-- 这些旧表不再被任何代码读写，仅用于存量数据复制，本脚本在 V7.2 之后执行，
-- 一次性 DROP 掉遗留旧表，关闭旧渠道表的生命周期。
--
-- 执行顺序约定：本脚本版本号为 V7.3，高于 account 的 V7.2；按
-- FlywayLikePopulator 的脚本记账顺序（INIT 按描述取最高版本；NORMAL 按
-- 文件名排序）落在 V7.2 之后执行，且只执行一次（执行记录去重）。
-- ---------------------------------------------------------------------------

-- 旧渠道表 DROP（IF EXISTS 幂等：表不存在则跳过；account 新表不存在时无意义清理也不执行）
DROP TABLE IF EXISTS `sys_ai_provider`;

DROP TABLE IF EXISTS `sys_ai_provider_model`;

DROP TABLE IF EXISTS `sys_ai_provider_connection`;

DROP TABLE IF EXISTS `sys_ai_channel_health`;
