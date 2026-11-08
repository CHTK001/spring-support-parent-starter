-- =====================================================================
-- V4.0.0.51__httpman.sql - spring-support-httpman-starter 建表脚本
-- 模块: spring-support-httpman-starter
-- 通道: INIT 建表（幂等：IF NOT EXISTS + 补列）
-- 说明: 接口调试控制台（HttpMan）能力，Postman 风格：集合 / 请求 / 历史 / 变量。
-- 命名规范: 列名使用完整表名作为前缀，如 ms_httpman_request_id
-- 数据库: MySQL 8.x，由 FlywayLike（plugin.datasource.script）自动扫描
-- 幂等: 建表 IF NOT EXISTS，重复执行安全
-- =====================================================================

-- ----------------------------------------------------------
-- 1. 集合（分类树）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_httpman_collection` (
    `ms_httpman_collection_id`          VARCHAR(64)     NOT NULL                COMMENT '主键（UUID）',
    `ms_httpman_collection_parent_id`   VARCHAR(64)     DEFAULT NULL            COMMENT '父级集合标识（NULL 根）',
    `ms_httpman_collection_name`        VARCHAR(128)    NOT NULL                COMMENT '集合名称',
    `ms_httpman_collection_remark`      VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
    `ms_httpman_collection_order`       INT             DEFAULT 0               COMMENT '排序',
    `ms_httpman_collection_expand`      TINYINT(1)      DEFAULT 0               COMMENT '是否展开 1是 0否',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`ms_httpman_collection_id`),
    KEY `ms_httpman_collection_parent` (`ms_httpman_collection_parent_id`),
    KEY `ms_httpman_collection_order` (`ms_httpman_collection_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HttpMan 集合';

-- ----------------------------------------------------------
-- 2. 请求（集合内树形组织，等价 Postman 的文件夹/数据）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_httpman_request` (
    `ms_httpman_request_id`           VARCHAR(64)     NOT NULL                COMMENT '主键（UUID）',
    `ms_httpman_request_collection_id` VARCHAR(64)    NOT NULL                COMMENT '所属集合标识',
    `ms_httpman_request_parent_id`    VARCHAR(64)     DEFAULT NULL            COMMENT '父级请求标识（目录/嵌套请求）',
    `ms_httpman_request_name`         VARCHAR(128)    DEFAULT NULL            COMMENT '请求名称',
    `ms_httpman_request_url`          VARCHAR(1024)   DEFAULT NULL            COMMENT '请求地址',
    `ms_httpman_request_method`       VARCHAR(16)     DEFAULT NULL            COMMENT '请求方法 GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD',
    `ms_httpman_request_headers`      TEXT            DEFAULT NULL            COMMENT '请求头 JSON',
    `ms_httpman_request_query`        TEXT            DEFAULT NULL            COMMENT '查询参数 JSON',
    `ms_httpman_request_body`         LONGTEXT        DEFAULT NULL            COMMENT '请求正文',
    `ms_httpman_request_body_type`    VARCHAR(32)     DEFAULT NULL            COMMENT '请求正文格式 json/form-data/x-www-form-urlencoded/none',
    `ms_httpman_request_auth`         TEXT            DEFAULT NULL            COMMENT '鉴权参数 JSON',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`ms_httpman_request_id`),
    KEY `ms_httpman_request_collection` (`ms_httpman_request_collection_id`),
    KEY `ms_httpman_request_parent` (`ms_httpman_request_parent_id`),
    KEY `ms_httpman_request_main` (`ms_httpman_request_collection_id`, `ms_httpman_request_parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HttpMan 请求';

-- ----------------------------------------------------------
-- 3. 历史（每次运行的完整记录，只追加）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_httpman_history` (
    `ms_httpman_history_id`            VARCHAR(64)    NOT NULL                COMMENT '主键（UUID）',
    `ms_httpman_history_request_id`    VARCHAR(64)    NOT NULL                COMMENT '请求标识',
    `ms_httpman_history_collection_id` VARCHAR(64)    NOT NULL                COMMENT '所属集合标识',
    `ms_httpman_history_request_name`  VARCHAR(128)   DEFAULT NULL            COMMENT '请求名称（冗余）',
    `ms_httpman_history_method`        VARCHAR(16)    DEFAULT NULL            COMMENT '请求方法（冗余）',
    `ms_httpman_history_url`           VARCHAR(1024)  DEFAULT NULL            COMMENT '请求地址（冗余）',
    `ms_httpman_history_status`        INT            DEFAULT NULL            COMMENT '响应状态码',
    `ms_httpman_history_cost_ms`       BIGINT         DEFAULT NULL            COMMENT '响应耗时（毫秒）',
    `ms_httpman_history_response_time` VARCHAR(32)    DEFAULT NULL            COMMENT '响应时间',
    `ms_httpman_history_request_headers` TEXT         DEFAULT NULL            COMMENT '发起请求头 JSON（已脱敏）',
    `ms_httpman_history_request_body`  LONGTEXT       DEFAULT NULL            COMMENT '发起请求正文（冗余）',
    `ms_httpman_history_response_headers` TEXT       DEFAULT NULL            COMMENT '响应头 JSON',
    `ms_httpman_history_response_body` LONGTEXT       DEFAULT NULL            COMMENT '响应正文',
    `ms_httpman_history_error`         LONGTEXT       DEFAULT NULL            COMMENT '错误信息',
    `ms_httpman_history_alert`         TINYINT(1)     DEFAULT 0               COMMENT '是否报警 1是 0否',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`ms_httpman_history_id`),
    KEY `ms_httpman_history_request` (`ms_httpman_history_request_id`),
    KEY `ms_httpman_history_collection` (`ms_httpman_history_collection_id`),
    KEY `ms_httpman_history_status` (`ms_httpman_history_status`),
    KEY `ms_httpman_history_time` (`ms_httpman_history_response_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HttpMan 运行历史';

-- ----------------------------------------------------------
-- 4. 变量（分类作用域：GLOBAL / COLLECTION / REQUEST）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_httpman_variable` (
    `ms_httpman_variable_id`           VARCHAR(64)     NOT NULL                COMMENT '主键（UUID）',
    `ms_httpman_variable_scope`        VARCHAR(32)     DEFAULT 'GLOBAL'        COMMENT '作用域 GLOBAL/COLLECTION/REQUEST',
    `ms_httpman_variable_collection_id` VARCHAR(64)    DEFAULT NULL            COMMENT '所属集合标识（COLLECTION 作用域时有效）',
    `ms_httpman_variable_name`         VARCHAR(128)    NOT NULL                COMMENT '变量名',
    `ms_httpman_variable_value`        VARCHAR(1024)   DEFAULT NULL            COMMENT '变量值',
    `ms_httpman_variable_remark`       VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`ms_httpman_variable_id`),
    KEY `ms_httpman_variable_scope` (`ms_httpman_variable_scope`),
    KEY `ms_httpman_variable_scope_collection` (`ms_httpman_variable_scope`, `ms_httpman_variable_collection_id`),
    KEY `ms_httpman_variable_collection` (`ms_httpman_variable_collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HttpMan 环境变量';
