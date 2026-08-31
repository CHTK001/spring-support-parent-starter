-- =====================================================================
-- 服务器系统配置表 (spring-support-server-starter)
-- key-value 存储在离线/采集/Agent/远控等系统设置
-- @since 4.0.0.42
-- =====================================================================

CREATE TABLE IF NOT EXISTS ms_server_config (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_config_key   VARCHAR(128) NOT NULL                COMMENT '配置键',
    ms_server_config_value VARCHAR(2048) NULL                   COMMENT '配置值(JSON字符串)',
    ms_server_config_desc  VARCHAR(255) NULL                    COMMENT '配置说明',
    create_by              INT          NULL                    COMMENT '创建人',
    create_name            VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time            DATETIME     NULL                    COMMENT '创建时间',
    update_name            VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by              INT          NULL                    COMMENT '更新人',
    update_time            DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_config_key (ms_server_config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器系统配置';
