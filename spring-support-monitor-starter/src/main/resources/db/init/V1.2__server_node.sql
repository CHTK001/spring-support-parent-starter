-- =====================================================================
-- 监控模块增量迁移: 服务管理 (spring-support-monitor-starter)
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- ---------------------------------------------------------------------
-- 协议服务器节点
-- 基于 common-starter Server 接口托管 HTTP / HTTP_PROXY / TCP_PROXY / SIP
-- 四类服务器的定义；运行期实例由 ServerNodeRegistry 拉起与回收
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_node (
    id                               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_node_name              VARCHAR(64)   NOT NULL              COMMENT '节点名称(唯一)',
    ms_server_node_protocol          VARCHAR(32)   NOT NULL              COMMENT '协议类型: HTTP / HTTP_PROXY / TCP_PROXY / SIP',
    ms_server_node_engine            VARCHAR(32)   DEFAULT 'jdk'         COMMENT '传输引擎: http= jdk/nio/aio, 代理= jdk, sip= sip',
    ms_server_node_host              VARCHAR(128)  DEFAULT '0.0.0.0'     COMMENT '监听地址',
    ms_server_node_port              INT           NOT NULL              COMMENT '监听端口',
    ms_server_node_enabled           TINYINT       NOT NULL DEFAULT 1    COMMENT '是否启用: 1 启用(随应用自动拉起), 0 停用',
    ms_server_node_remark            VARCHAR(255)  NULL                  COMMENT '备注',
    ms_server_node_config            TEXT          NULL                  COMMENT '协议专属配置(JSON): sip.token, proxy.backendHost/backendPort 等',
    ms_server_node_filters           TEXT          NULL                  COMMENT 'ServerFilter 插件配置(JSON 数组)',
    create_name                      VARCHAR(64)   DEFAULT NULL          COMMENT '创建人姓名',
    create_by                        INT           DEFAULT NULL          COMMENT '创建人',
    create_time                      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                      VARCHAR(64)   DEFAULT NULL          COMMENT '更新人姓名',
    update_by                        INT           DEFAULT NULL          COMMENT '更新人',
    update_time                      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ms_server_node_name (ms_server_node_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务管理-协议服务器节点';
