-- =====================================================================
-- V1.0__initdata_all_server.sql - 服务器模块初始化数据
-- 旧版升级脚本中的 Windows SSH 兼容数据与系统配置种子统一归入本脚本。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统配置初始化数据（原 V1.0__initdata_server.sql，INSERT IGNORE 幂等）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.host', '"124.221.230.112"', 'guacd 网关地址（远控隧道）');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.port', '"4822"', 'guacd 网关端口（远控隧道）');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.client.host', '""', 'guacd-client 网关地址（浏览器侧 WS 隧道 host:port，空串=同域）');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.sweep.interval', '"60"', '连接缓存健康检查间隔(秒)');

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.idle.timeout', '"300"', '连接空闲超时(秒)');

-- 历史 MySQL/H2 SSH 开关增量：Windows 主机兼容初始化。
UPDATE ms_server_host SET ms_server_host_ssh_enabled = 0 WHERE UPPER(ms_server_host_os_type) = 'WINDOWS';
