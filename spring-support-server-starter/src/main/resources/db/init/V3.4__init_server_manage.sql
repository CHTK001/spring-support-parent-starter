-- ============================================================
-- 服务管理：协议服务器节点与操作日志
-- ============================================================
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
    UNIQUE KEY `ms_server_node_ms_server_node_name` (ms_server_node_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务管理-协议服务器节点';

CREATE TABLE IF NOT EXISTS `ms_server_oper_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `node_id`     BIGINT       NOT NULL COMMENT '节点 id（ms_server_node.id）',
    `node_name`   VARCHAR(128) NULL DEFAULT '' COMMENT '节点名称（冗余，节点删除后仍可读）',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作码：CREATE/UPDATE/HOT_UPDATE/FILTER_*/START/STOP/DELETE',
    `action_name` VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`      VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（协议与监听地址摘要），可空',
    `detail`      TEXT         NULL COMMENT '补充说明',
    `success`     TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`     BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`    VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`   VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url` VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time` DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_server_oper_log_node_time` (`node_id`, `create_time`),
    KEY `idx_ms_server_oper_log_action` (`node_id`, `action`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务管理节点操作日志';
