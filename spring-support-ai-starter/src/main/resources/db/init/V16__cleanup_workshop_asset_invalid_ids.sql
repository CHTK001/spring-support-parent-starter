-- V16__cleanup_workshop_asset_invalid_ids.sql
-- 清理 workshop_asset 表中不符合 UUIDv7 契约的历史脏数据：
-- 旧版本用 32 位十六进制（无连字符）生成 asset_id，前端 parseAssetId 严格校验 UUIDv7 会导致整列表加载失败。
-- 这些记录的对应文件在磁盘上已不存在（孤儿测试数据），直接删除。

DELETE FROM `workshop_asset`
WHERE `asset_id` NOT REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$';

SELECT 1;
