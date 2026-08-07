/*
  V1.0__init_job.sql

  跨数据库通用初始化 SQL (MySQL 8 / PostgreSQL 12 / Oracle 12c)

  设计原则:
    1. 字段全部以表名为前缀
    2. 不使用方言专属语法 (ENGINE / IF NOT EXISTS / AUTO_INCREMENT)
    3. 自增主键由各数据库方言层处理 (MySQL AUTO_INCREMENT 由 ORM / DDL 配置)

  切库注意事项:
    - PostgreSQL / Oracle 切换时, 需要调整 IdType (ASSIGN_ID) 配合序列
    - MySQL 可继续使用 AUTO_INCREMENT
*/

CREATE TABLE sys_job (
    -- 主表字段
    sys_job_id                       BIGINT       NOT NULL           COMMENT '任务 ID',
    sys_job_no                       VARCHAR(64)  NOT NULL           COMMENT '业务编号',
    sys_job_name                     VARCHAR(255)                   COMMENT '任务名',
    sys_job_group                    VARCHAR(64)  DEFAULT 'default'  COMMENT '命名空间',
    sys_job_schedule_type            VARCHAR(50)  DEFAULT 'CRON'     COMMENT '调度类型, CRON/FIXED/FIXED_MS/DELAY/AT/NONE',
    sys_job_schedule_time            VARCHAR(255)                   COMMENT '调度配置, cron 或固定间隔',
    sys_job_trigger_status           INT          DEFAULT 0         COMMENT '调度状态, 0-停止 1-运行',
    sys_job_desc                     VARCHAR(500)                   COMMENT '描述',
    sys_job_glue_updatetime          DATETIME                       COMMENT 'GLUE 更新时间',
    sys_job_glue_source              CLOB                           COMMENT 'GLUE 源码',
    sys_job_glue_type                VARCHAR(50)  DEFAULT 'BEAN'    COMMENT 'GLUE 类型',
    sys_job_dispatch_mode            VARCHAR(20)  DEFAULT 'LOCAL'   COMMENT '分发模式, LOCAL/REMOTE',
    sys_job_remote_executor_address  VARCHAR(500)                   COMMENT '远程执行器地址',
    sys_job_fail_retry               INT          DEFAULT 3         COMMENT '失败重试次数',
    sys_job_retry_interval           INT          DEFAULT 0         COMMENT '重试间隔, 秒',
    sys_job_execute_timeout          INT          DEFAULT 0         COMMENT '执行超时, 秒',
    sys_job_execute_bean             VARCHAR(255)                   COMMENT '执行器 handler 名称',
    sys_job_execute_param            VARCHAR(512)                   COMMENT '执行参数',
    sys_job_exception_callback_bean  VARCHAR(255)                   COMMENT '异常回调 handler',
    sys_job_retry_callback_bean      VARCHAR(255)                   COMMENT '重试前回调 handler',
    sys_job_execute_misfire_strategy VARCHAR(50)  DEFAULT 'DO_NOTHING' COMMENT '错失策略',
    sys_job_trigger_last_time        BIGINT       DEFAULT 0         COMMENT '上次触发时间, 毫秒',
    sys_job_trigger_next_time        BIGINT       DEFAULT 0         COMMENT '下次触发时间, 毫秒',
    sys_job_is_deleted               INT          DEFAULT 0         COMMENT '逻辑删除, 0-未删 1-已删',
    sys_job_version                  BIGINT       DEFAULT 0         COMMENT '乐观锁版本',

    -- 通用审计字段
    create_name                      VARCHAR(64)                    COMMENT '创建人姓名',
    create_by                        INT                            COMMENT '创建人',
    create_time                      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                      VARCHAR(64)                    COMMENT '更新人姓名',
    update_by                        INT                            COMMENT '更新人',
    update_time                      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (sys_job_id),
    CONSTRAINT uk_sys_job_no UNIQUE (sys_job_no)
);

CREATE INDEX idx_sys_job_trigger_status    ON sys_job (sys_job_trigger_status);
CREATE INDEX idx_sys_job_trigger_next_time  ON sys_job (sys_job_trigger_next_time);

COMMENT ON TABLE sys_job IS '任务主表';


CREATE TABLE sys_job_log (
    -- 日志字段
    sys_job_log_id              BIGINT       NOT NULL           COMMENT '日志 ID',
    sys_job_log_no              VARCHAR(64)  NOT NULL           COMMENT '日志编号',
    sys_job_id                  BIGINT                        COMMENT '任务 ID',
    sys_job_no                  VARCHAR(64)                    COMMENT '任务编号',
    sys_job_log_app             VARCHAR(255)                   COMMENT '触发应用',
    sys_job_log_trigger_bean    VARCHAR(255)                   COMMENT 'handler 名',
    sys_job_log_trigger_type    VARCHAR(50)                    COMMENT '触发类型',
    sys_job_log_profile         VARCHAR(50)                    COMMENT 'profile',
    sys_job_log_trigger_time    DATETIME                       COMMENT '触发时间',
    sys_job_log_trigger_date    DATE                           COMMENT '触发日期',
    sys_job_log_trigger_code    VARCHAR(20)                    COMMENT '触发状态码',
    sys_job_log_execute_code    VARCHAR(20)  DEFAULT 'PADDING' COMMENT '执行状态码',
    sys_job_log_cost            DECIMAL(10, 2)                 COMMENT '耗时, ms',
    sys_job_log_trigger_msg     CLOB                           COMMENT '消息',
    sys_job_log_trigger_param   VARCHAR(512)                   COMMENT '参数',
    sys_job_log_trigger_address VARCHAR(255)                   COMMENT '地址',
    sys_job_log_node_id         VARCHAR(64)                    COMMENT '节点 ID',
    sys_job_log_file_path       VARCHAR(500)                   COMMENT '日志文件',
    sys_job_log_is_deleted      INT          DEFAULT 0         COMMENT '逻辑删除',

    -- 通用审计字段
    create_name                 VARCHAR(64)                    COMMENT '创建人姓名',
    create_by                   INT                            COMMENT '创建人',
    create_time                 DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                 VARCHAR(64)                    COMMENT '更新人姓名',
    update_by                   INT                            COMMENT '更新人',
    update_time                 DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (sys_job_log_id),
    CONSTRAINT uk_sys_job_log_no UNIQUE (sys_job_log_no)
);

CREATE INDEX idx_sys_job_log_job_id        ON sys_job_log (sys_job_id);
CREATE INDEX idx_sys_job_log_node_id       ON sys_job_log (sys_job_log_node_id);
CREATE INDEX idx_sys_job_log_trigger_date  ON sys_job_log (sys_job_log_trigger_date);

COMMENT ON TABLE sys_job_log IS '任务执行日志';
