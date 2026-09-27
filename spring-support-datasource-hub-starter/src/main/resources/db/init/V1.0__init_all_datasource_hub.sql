-- ============================================================
-- 数据源中心建表脚本（MySQL / H2 双库可执行子集）
-- 模块: spring-support-datasource-hub-starter
-- 说明: 数据源台账，供监控/采集等系统共享
-- ============================================================

CREATE TABLE IF NOT EXISTS `ms_datasource`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_name`        VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `datasource_type`        VARCHAR(32)  NOT NULL COMMENT '数据源类型(jdbc/redis/influxdb/neo4j/nacos/zookeeper/nginx)',
    `datasource_host`        VARCHAR(255) NULL DEFAULT '' COMMENT '主机地址',
    `datasource_port`        INT          NULL COMMENT '端口，空则按类型默认',
    `datasource_database`    VARCHAR(512) NULL DEFAULT '' COMMENT '库名/命名空间；jdbc 可存完整 JDBC URL',
    `datasource_username`    VARCHAR(128) NULL DEFAULT '' COMMENT '用户名',
    `datasource_password`    VARCHAR(512) NULL DEFAULT '' COMMENT '口令',
    `datasource_remark`      VARCHAR(512) NULL DEFAULT '' COMMENT '备注',
    `datasource_status`      INT          NULL DEFAULT 1 COMMENT '状态 - 0:停用, 1:启用；停用的数据源不允许被流程引用',
    `datasource_driver_class_name` VARCHAR(255) NULL DEFAULT '' COMMENT 'JDBC 驱动类名，jdbc 类型非 MySQL 时可显式指定',
    `datasource_create_time` DATETIME     NULL COMMENT '创建时间',
    `datasource_update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) COMMENT '数据源台账';

CREATE INDEX IF NOT EXISTS `idx_ms_datasource_name` ON `ms_datasource` (`datasource_name`);
CREATE INDEX IF NOT EXISTS `idx_ms_datasource_type` ON `ms_datasource` (`datasource_type`);

-- ------------------------------------------------------------
-- 连通性指标（点「测试连接」时回写，覆盖写、不留历史）
-- connect_latency：建立连接耗时（TCP + 协议握手）
-- session_latency：已建立连接上一次往返耗时（jdbc=SELECT 1 / redis=PING / 其余=TCP 探活）
-- ------------------------------------------------------------
ALTER TABLE `ms_datasource`
    ADD COLUMN `datasource_connect_latency_ms` BIGINT NULL COMMENT '连接延迟(毫秒)，最近一次测试回写' AFTER `datasource_status`,
    ADD COLUMN `datasource_session_latency_ms` BIGINT NULL COMMENT '会话延迟(毫秒)，最近一次测试回写' AFTER `datasource_connect_latency_ms`,
    ADD COLUMN `datasource_last_test_time` DATETIME NULL COMMENT '最近一次连通性测试时间' AFTER `datasource_session_latency_ms`,
    ADD COLUMN `datasource_last_test_ok` INT NULL COMMENT '最近一次测试是否连通 - 0:失败, 1:成功' AFTER `datasource_last_test_time`;

-- ------------------------------------------------------------
-- SSH 隧道配置（高级配置）
-- 一个数据源至多一条配置；tunnel_type 对应 LOCAL(-L)/REMOTE(-R 反向)/DYNAMIC(SOCKS5)
-- 目标库在内网无法直连时，用反向隧道把远端可达的端口映射回本机再连
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_datasource_tunnel`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`   BIGINT       NOT NULL COMMENT '数据源 ID',
    `tunnel_name`     VARCHAR(128) NULL DEFAULT '' COMMENT '隧道名称',
    `tunnel_type`     VARCHAR(16)  NOT NULL COMMENT '隧道类型 - LOCAL:本地转发(-L), REMOTE:反向转发(-R), DYNAMIC:动态SOCKS5',
    `ssh_host`        VARCHAR(255) NOT NULL COMMENT 'SSH 服务器地址',
    `ssh_port`        INT          NULL COMMENT 'SSH 端口，空则 22',
    `ssh_username`    VARCHAR(128) NULL DEFAULT '' COMMENT 'SSH 用户名',
    `ssh_auth_type`   VARCHAR(16)  NULL DEFAULT 'PASSWORD' COMMENT '认证方式 - PASSWORD:口令, KEY:私钥',
    `ssh_password`    VARCHAR(512) NULL DEFAULT '' COMMENT 'SSH 口令（ssh_auth_type=PASSWORD 时使用）',
    `ssh_private_key` TEXT         NULL COMMENT 'SSH 私钥内容（ssh_auth_type=KEY 时使用）',
    `ssh_passphrase`  VARCHAR(512) NULL DEFAULT '' COMMENT '私钥口令',
    `local_port`      INT          NULL COMMENT '本地端口，空则自动分配',
    `remote_host`     VARCHAR(255) NULL DEFAULT '' COMMENT '远端目标主机（DYNAMIC 时为空）',
    `remote_port`     INT          NULL COMMENT '远端目标端口（DYNAMIC 时为空）',
    `bind_address`    VARCHAR(128) NULL DEFAULT '127.0.0.1' COMMENT '本地绑定地址',
    `tunnel_status`   INT          NULL DEFAULT 0 COMMENT '状态 - 0:停用, 1:启用',
    `create_time`     DATETIME     NULL COMMENT '创建时间',
    `update_time`     DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_datasource_tunnel_ds` (`datasource_id`)
) COMMENT '数据源 SSH 隧道配置（高级配置）';