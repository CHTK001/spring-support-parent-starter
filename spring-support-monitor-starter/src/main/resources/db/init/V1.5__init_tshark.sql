-- ============================================================
-- TShark 抓包建表脚本
-- 模块: spring-support-monitor-starter
-- 背景: MsTsharkSession / MsTsharkPacket 两个实体长期无对应 DDL，
--       抓包结果落库必然报"表不存在"，故单列本脚本并在
--       spring.sql.init.schema-locations 中追加加载。
-- 数据库: MySQL 8.x / H2 (MySQL 模式)
-- 幂等: 建表 IF NOT EXISTS，重复执行安全
-- ============================================================

-- ----------------------------------------------------------
-- 1. 抓包会话
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_tshark_session` (
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_tshark_session_name`        VARCHAR(128) DEFAULT NULL            COMMENT '会话名称',
    `ms_tshark_session_interface`   VARCHAR(128) DEFAULT NULL            COMMENT '抓包网卡',
    `ms_tshark_session_filter`      VARCHAR(512) DEFAULT NULL            COMMENT 'BPF 过滤器',
    `ms_tshark_session_status`      TINYINT      DEFAULT 0               COMMENT '状态: 0停止 1运行中',
    `ms_tshark_session_packet_count` INT         DEFAULT 0               COMMENT '已入库数据包数',
    `ms_tshark_session_file_path`   VARCHAR(512) DEFAULT NULL            COMMENT 'pcap 文件路径',
    `ms_tshark_session_duration`    INT          DEFAULT NULL            COMMENT '抓包时长(秒)',
    `ms_tshark_session_start_time`  DATETIME     DEFAULT NULL            COMMENT '开始时间',
    `ms_tshark_session_end_time`    DATETIME     DEFAULT NULL            COMMENT '结束时间',
    `create_name`                   VARCHAR(64)  NULL                    COMMENT '创建人姓名',
    `create_by`                     INT          NULL                    COMMENT '创建人',
    `create_time`                   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                   VARCHAR(64)  NULL                    COMMENT '更新人姓名',
    `update_by`                     INT          NULL                    COMMENT '更新人',
    `update_time`                   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_tshark_session_status` (`ms_tshark_session_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TShark 抓包会话';

-- ----------------------------------------------------------
-- 2. 抓包数据包
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_tshark_packet` (
    `id`                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_tshark_packet_session_id` BIGINT      NOT NULL                COMMENT '所属会话 ID',
    `ms_tshark_packet_time`      DATETIME(3)  DEFAULT NULL            COMMENT '报文时间(毫秒精度)',
    `ms_tshark_packet_source_ip` VARCHAR(64)  DEFAULT NULL            COMMENT '源 IP',
    `ms_tshark_packet_dest_ip`   VARCHAR(64)  DEFAULT NULL            COMMENT '目的 IP',
    `ms_tshark_packet_source_port` INT        DEFAULT NULL            COMMENT '源端口',
    `ms_tshark_packet_dest_port` INT          DEFAULT NULL            COMMENT '目的端口',
    `ms_tshark_packet_protocol`  VARCHAR(64)  DEFAULT NULL            COMMENT '协议分层',
    `ms_tshark_packet_length`    INT          DEFAULT NULL            COMMENT '报文长度',
    `ms_tshark_packet_info`      VARCHAR(1024) DEFAULT NULL           COMMENT '摘要信息',
    `ms_tshark_packet_raw`       LONGTEXT     DEFAULT NULL            COMMENT '解码后的 JSON 原文',
    `ms_tshark_packet_restored`  LONGTEXT     DEFAULT NULL            COMMENT '还原出的载荷文本',
    `create_name`                VARCHAR(64)  NULL                    COMMENT '创建人姓名',
    `create_by`                  INT          NULL                    COMMENT '创建人',
    `create_time`                DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                VARCHAR(64)  NULL                    COMMENT '更新人姓名',
    `update_by`                  INT          NULL                    COMMENT '更新人',
    `update_time`                DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_tshark_packet_session` (`ms_tshark_packet_session_id`, `id`),
    KEY `idx_ms_tshark_packet_protocol` (`ms_tshark_packet_protocol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TShark 抓包数据包';
