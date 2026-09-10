-- =====================================================================
-- 服务器资产模块初始化数据脚本 (spring-support-server-starter)
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明: 本文件为唯一初始化数据脚本, 写入全局系统配置默认值
-- 主机级配置默认值在新增主机时由后端写入, 无需在此初始化
-- INSERT IGNORE 依赖唯一键(uk_server_config_key)保证幂等
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- Guacd 远控网关配置（guacd = 守护进程地址；guacd-client = 浏览器侧 WS 隧道网关，空串=同域 location.host）
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.host', '"172.16.0.40"', 'guacd 网关地址（远控隧道）');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.port', '"14822"', 'guacd 网关端口（远控隧道）');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.client.host', '"127.0.0.1:8082"', 'guacd-client 网关地址（浏览器侧 WS 隧道 host:port，空串=同域）');

-- 全局连接缓存配置
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.sweep.interval', '"60"', '连接缓存健康检查间隔(秒)');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.idle.timeout', '"300"', '连接空闲超时(秒)');
