-- ============================================================================
-- V1__init.sql - AI 中台初始化脚本（合并版）
-- 由 db/init/*.sql + schema.sql 合并生成。
-- ============================================================================
-- ============================================================
-- PART 1: 表结构（DDL）
-- ============================================================
-- 1. 清理遗留表
-- 2. 表结构
-- 3. 列变更（ALTER TABLE）
-- 4. 动态列变更（幂等 PREPARE/EXECUTE）
-- ============================================================
-- PART 2: 初始化数据（DML）
-- ============================================================
INSERT IGNORE INTO `sys_ai_face_group` (`sys_ai_face_group_name`, `sys_ai_face_group_sort`)
INSERT IGNORE INTO `sys_ai_knowledge_library` (
INSERT IGNORE INTO `sys_ai_knowledge_library` (
INSERT INTO `sys_ai_manufacturer` (
UPDATE `sys_ai_manufacturer`
INSERT INTO `sys_ai_module` (
UPDATE `sys_ai_module`
INSERT INTO `sys_ai_module` (
INSERT INTO `sys_ai_global_setting` (
DELETE FROM `sys_ai_module`
UPDATE `sys_ai_module`
INSERT IGNORE INTO `sys_ai_group` (`sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`, `sys_ai_group_sort`, `sys_ai_group_status`)
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
INSERT IGNORE INTO `sys_ai_knowledge_library` (
INSERT IGNORE INTO `sys_ai_knowledge_library` (
INSERT INTO `sys_ai_manufacturer` (
INSERT INTO `sys_ai_module` (
INSERT INTO `sys_ai_module` (
INSERT INTO `sys_ai_global_setting` (
INSERT IGNORE INTO `sys_ai_group` (`sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`, `sys_ai_group_sort`, `sys_ai_group_status`)
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
INSERT IGNORE INTO `sys_ai_group_model` (
INSERT IGNORE INTO `sys_ai_pricing` (
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
INSERT INTO sys_agent_browser_host (host_id, state, epoch, headful, identity_mode, lane_count, rss_bytes, created_at, updated_at)
-- V11__ensure_sys_ai_requirement.sql
-- 补充 sys_ai_requirement 表（V1 重构后新增的表，因既有库跳过 V1 而未创建）
-- V12__data_source.sql
-- 需求数据源配置表（支持飞书多维表格、Jira、Notion、通用 REST API 等）
-- V13__fix_data_source_timestamps.sql
-- 修复 sys_ai_data_source 表的 create_time/update_time 列类型从 DATETIME 改为 BIGINT
-- V14__ensure_sys_ai_usage_columns.sql
-- 补充 sys_ai_usage 扩展列（原 V10 被重构删除、V1 未包含，既有库缺失导致列表/统计查询报 Unknown column）
-- 注意：不使用存储过程（Spring ScriptUtils 无法解析 BEGIN...END），改为 PREPARE/EXECUTE 动态 SQL 实现 IF NOT EXISTS 幂等语义
-- V15__add_daily_usage_stats_columns.sql
-- 为 sys_ai_usage_daily 日汇总表补充统计列：
--   cached_tokens   缓存读取 Token 合计
--   total_latency   总耗时（毫秒）合计，用于计算平均耗时
--   standard_cost   标准费用（官方价格，未折扣）合计
-- V16__cleanup_workshop_asset_invalid_ids.sql
-- 清理 workshop_asset 表中不符合 UUIDv7 契约的历史脏数据：
-- 旧版本用 32 位十六进制（无连字符）生成 asset_id，前端 parseAssetId 严格校验 UUIDv7 会导致整列表加载失败。
-- 这些记录的对应文件在磁盘上已不存在（孤儿测试数据），直接删除。
DELETE FROM `workshop_asset`
