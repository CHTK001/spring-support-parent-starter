-- =====================================================================
-- 爬虫中心数据库 Schema（MySQL 8.x）。
--
-- 表前缀 spider_ 保留：与 spring-support-* / utils-support-* 的其他模块保持列前缀隔离。
-- 表内字段统一使用 spider_* 前缀以避免与其他业务表冲突。
--
-- 所有表均使用 InnoDB + utf8mb4。
-- =====================================================================

-- ==================== 爬虫定义 ====================

CREATE TABLE IF NOT EXISTS spider_definition (
    spider_id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    spider_code               VARCHAR(64)  NOT NULL                COMMENT '爬虫业务编码',
    spider_name               VARCHAR(128) NOT NULL                COMMENT '爬虫名称',
    spider_entry_url          VARCHAR(1024) NOT NULL               COMMENT '入口 URL',
    spider_site_host          VARCHAR(128) DEFAULT NULL            COMMENT '站点主机',
    spider_executor_type      VARCHAR(16)  DEFAULT 'ONCE'          COMMENT '调度类型',
    spider_executor_rounds    INT          DEFAULT 1               COMMENT '调度轮次',
    spider_collect_deep_url   TINYINT      DEFAULT 1               COMMENT '是否深度采集 0/1',
    spider_max_depth          INT          DEFAULT 1               COMMENT '最大爬取深度',
    spider_max_pages          INT          DEFAULT 50              COMMENT '最大页数',
    spider_dedup_strategy     VARCHAR(32)  DEFAULT 'URL_FINGERPRINT' COMMENT '去重策略',
    spider_url_storage_type   VARCHAR(32)  DEFAULT 'MEMORY'       COMMENT 'URL 存储类型',
    spider_status             TINYINT      DEFAULT 1               COMMENT '启停状态 0/1',
    spider_remark             VARCHAR(512) DEFAULT NULL            COMMENT '备注',
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (spider_id),
    UNIQUE KEY uk_spider_code (spider_code),
    KEY idx_spider_status (spider_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '爬虫定义';

-- ==================== 采集数据记录 ====================

CREATE TABLE IF NOT EXISTS spider_data_record (
    spider_id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    spider_definition_code    VARCHAR(64)  NOT NULL                COMMENT '爬虫编码',
    spider_execution_no       VARCHAR(64)  NOT NULL                COMMENT '执行批次号',
    spider_target_url         VARCHAR(2048) NOT NULL               COMMENT '目标 URL',
    spider_data_code          VARCHAR(64)  DEFAULT NULL            COMMENT '数据 schema 编码',
    spider_data_name          VARCHAR(512) DEFAULT NULL            COMMENT '数据项名称',
    spider_data_payload       MEDIUMTEXT   DEFAULT NULL            COMMENT 'JSON 载荷',
    spider_data_status        VARCHAR(16)  DEFAULT 'SUCCESS'       COMMENT '结果状态',
    spider_depth              INT          DEFAULT 0               COMMENT '爬取深度 0=列表/N=第N层详情',
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (spider_id),
    KEY idx_record_definition (spider_definition_code),
    KEY idx_record_execution (spider_execution_no),
    KEY idx_record_depth (spider_definition_code, spider_depth),
    KEY idx_record_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采集数据记录';

-- ==================== 爬虫执行记录 ====================

CREATE TABLE IF NOT EXISTS spider_execution_record (
    spider_id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    spider_execution_no       VARCHAR(64)  NOT NULL                COMMENT '执行批次号',
    spider_definition_code    VARCHAR(64)  NOT NULL                COMMENT '爬虫编码',
    spider_trigger_type       VARCHAR(16)  DEFAULT 'MANUAL'        COMMENT '触发方式',
    spider_execution_status   VARCHAR(16)  DEFAULT 'RUNNING'       COMMENT '执行状态',
    spider_max_depth_reached  INT          DEFAULT 0               COMMENT '本次到达的最大深度',
    spider_collected_count    INT          DEFAULT 0               COMMENT '采集条数',
    spider_url_total          INT          DEFAULT 0               COMMENT 'URL 总数',
    spider_url_completed      INT          DEFAULT 0               COMMENT 'URL 完成数',
    spider_started_at         DATETIME     DEFAULT NULL            COMMENT '开始时间',
    spider_finished_at        DATETIME     DEFAULT NULL            COMMENT '结束时间',
    spider_error_message      TEXT         DEFAULT NULL            COMMENT '错误信息',
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (spider_id),
    UNIQUE KEY uk_execution_no (spider_execution_no),
    KEY idx_execution_definition (spider_definition_code),
    KEY idx_execution_status (spider_execution_status),
    KEY idx_execution_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '爬虫执行记录';