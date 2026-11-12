-- =====================================================================
-- V6.8__alter_service_access_log_router_group.sql
-- 通道: ALTER 变更
-- 说明: 路由拓扑页需要回答「这一小时里，打到【这个路由组】的流量是谁发的」。
--
--   1) router_group  本次调用归属的路由组（路由组供应商 ID）。
--                    网关请求模型是「供应商ID:模型名」，前缀即路由组；
--                    控制台直连与服务实例调用为空。
--   2) 索引          拓扑页每 2 秒按 (router_group, create_time) 收敛一次窗口，
--                    没有这个复合索引就是全表扫描。
--
-- 写法：information_schema 判存 + 动态 DDL，可反复执行。
-- =====================================================================

-- ---------- 1. 路由组归属 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_access_log'
          AND COLUMN_NAME = 'router_group'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_access_log` ADD COLUMN `router_group` VARCHAR(64) DEFAULT NULL
             COMMENT ''本次调用归属的路由组（路由组供应商 ID；控制台直连为空）'' AFTER `app_key`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2. 按路由组 + 时间窗聚合 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_access_log'
          AND INDEX_NAME = 'sys_ai_service_access_log_router_group'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_access_log` ADD KEY `sys_ai_service_access_log_router_group` (`router_group`, `create_time`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
