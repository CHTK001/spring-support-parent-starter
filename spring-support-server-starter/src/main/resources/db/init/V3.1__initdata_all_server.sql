-- =====================================================================
-- V3.1__initdata_all_server.sql - spring-support-server-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。随 V3.1 表/列重命名
--   （ms_ 前缀统一为 server_ 前缀）同步改写目标表与列名。
-- 约束: 全部语句必须幂等（INSERT IGNORE / 条件 UPDATE），可反复执行。
-- @author CH
-- @since 4.0.0.51
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统配置初始化数据（INSERT IGNORE 幂等）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO server_config (server_config_key, server_config_value, server_config_desc)
VALUES ('guacd.host', '"124.221.230.112"', 'guacd 网关地址（远控隧道）');

INSERT IGNORE INTO server_config (server_config_key, server_config_value, server_config_desc)
VALUES ('guacd.port', '"4822"', 'guacd 网关端口（远控隧道）');

INSERT IGNORE INTO server_config (server_config_key, server_config_value, server_config_desc)
VALUES ('guacd.client.host', '""', 'guacd-client 网关地址（浏览器侧 WS 隧道 host:port，空串=同域）');

INSERT IGNORE INTO server_config (server_config_key, server_config_value, server_config_desc)
VALUES ('connection.sweep.interval', '"60"', '连接缓存健康检查间隔(秒)');

INSERT IGNORE INTO server_config (server_config_key, server_config_value, server_config_desc)
VALUES ('connection.idle.timeout', '"300"', '连接空闲超时(秒)');

-- 历史 MySQL/H2 SSH 开关增量：Windows 主机兼容初始化。
UPDATE server_host SET server_host_ssh_enabled = 0 WHERE UPPER(server_host_os_type) = 'WINDOWS';
