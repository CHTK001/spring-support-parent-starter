/*
  V1.0__init_job.sql

  任务调度模块数据库初始化脚本 (单一脚本)

  设计原则:
    1. 字段全部以表名为前缀
    2. 仅使用三库通用语法 (MySQL 8 / PostgreSQL 12 / Oracle 12c), 不使用方言专属关键字:
       - 列/表注释统一使用 "--" 行注释, 不使用 MySQL 内联 COMMENT 或 PG/Oracle 的 COMMENT ON
       - 64 位整型统一使用 NUMERIC(19, 0), 兼容 Oracle NUMBER
       - 时间类型统一使用 TIMESTAMP / DATE, 不使用 MySQL 专属 DATETIME
       - 大文本统一使用 VARCHAR(4000), 兼容 Oracle VARCHAR2 上限
    3. 主键由 ORM 层 (MyBatis-Plus) 生成, 不使用 AUTO_INCREMENT / 序列
    4. 数据库方言差异 (分页/类型) 由 spring-support-mybatis-starter 内部自动适配,
       本脚本仅需保证三库自兼容即可

  包含表:
    - sys_job:          任务主表
    - sys_job_log:      任务执行日志表
    - sys_job_cluster:  集群选主表

  切库注意事项:
    - 脚本由 spring-support-datasource-starter 的 FlywayLikePopulator 执行,
      采用 Spring ScriptUtils 原样执行, 不区分方言, 故本文件必须三库自兼容
    - Oracle 中 DEFAULT 子句不支持函数表达式, 故时间字段不设置数据库默认值,
      由 ORM 审计字段自动填充
*/

-- =====================================================
-- 1. 任务主表 (sys_job)
-- 用途: 保存任务定义、调度配置与触发时间状态
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_job (
    -- 主键: 任务 ID
    sys_job_id                       NUMERIC(19, 0) NOT NULL,
    -- 业务编号: 非主键, 对外唯一
    sys_job_no                       VARCHAR(64)    NOT NULL,
    -- 任务名
    sys_job_name                     VARCHAR(255),
    -- 任务负责人: 留扩展, 便于告警通知
    sys_job_author                   VARCHAR(64),
    -- 告警邮箱: 多个用逗号分隔
    sys_job_alarm_email              VARCHAR(255),
    -- 命名空间: 隔离多项目
    sys_job_group                    VARCHAR(64)    DEFAULT 'default',
    -- 调度类型: CRON / FIXED / FIXED_MS / DELAY / AT / NONE
    sys_job_schedule_type            VARCHAR(50)    DEFAULT 'CRON',
    -- 调度配置: cron 表达式或毫秒间隔
    sys_job_schedule_time            VARCHAR(255),
    -- 调度状态: 0-停止 1-运行
    sys_job_trigger_status           INT            DEFAULT 0,
    -- 任务描述
    sys_job_desc                     VARCHAR(500),
    -- GLUE 更新时间: 留扩展
    sys_job_glue_updatetime          TIMESTAMP,
    -- GLUE 源码: 留扩展, 统一 VARCHAR(4000) 以兼容 Oracle
    sys_job_glue_source              VARCHAR(4000),
    -- GLUE 类型: BEAN / GLUE_xxx
    sys_job_glue_type                VARCHAR(50)    DEFAULT 'BEAN',
    -- 分发模式: LOCAL / REMOTE
    sys_job_dispatch_mode            VARCHAR(20)    DEFAULT 'LOCAL',
    -- 远程执行器地址覆盖
    sys_job_remote_executor_address  VARCHAR(500),
    -- 日志存储模式: DATABASE / FILE
    sys_job_storage_mode             VARCHAR(20)    DEFAULT 'DATABASE',
    -- 失败重试次数
    sys_job_fail_retry               INT            DEFAULT 3,
    -- 重试间隔: 秒
    sys_job_retry_interval           INT            DEFAULT 0,
    -- 执行超时: 秒
    sys_job_execute_timeout          INT            DEFAULT 0,
    -- 执行器 Handler 名称
    sys_job_execute_bean             VARCHAR(255),
    -- 执行参数
    sys_job_execute_param            VARCHAR(512),
    -- 异常回调 Handler
    sys_job_exception_callback_bean  VARCHAR(255),
    -- 重试前回调 Handler
    sys_job_retry_callback_bean      VARCHAR(255),
    -- 错失触发策略
    sys_job_execute_misfire_strategy VARCHAR(50)    DEFAULT 'DO_NOTHING',
    -- 上次触发时间: 毫秒时间戳
    sys_job_trigger_last_time        NUMERIC(19, 0) DEFAULT 0,
    -- 下次触发时间: 毫秒时间戳
    sys_job_trigger_next_time        NUMERIC(19, 0) DEFAULT 0,
    -- 逻辑删除: 0-未删 1-已删
    sys_job_is_deleted               INT            DEFAULT 0,
    -- 乐观锁版本
    sys_job_version                  NUMERIC(19, 0) DEFAULT 0,

    -- 通用审计字段 (由 ORM 审计填充, 参考 SysBase)
    create_name                      VARCHAR(64),
    create_by                        INT,
    create_time                      TIMESTAMP,
    update_name                      VARCHAR(64),
    update_by                        INT,
    update_time                      TIMESTAMP,

    PRIMARY KEY (sys_job_id),
    CONSTRAINT uk_sys_job_no UNIQUE (sys_job_no),
    KEY `idx_sys_job_trigger_status` (`sys_job_trigger_status`),
    KEY `idx_sys_job_trigger_next_time` (`sys_job_trigger_next_time`)
);

-- 任务状态索引: 用于按调度状态扫描任务
-- 下次触发时间索引: 用于调度器轮询到期任务


-- =====================================================
-- 2. 任务执行日志表 (sys_job_log)
-- 用途: 记录任务触发与执行结果, 供运维排查与统计
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_job_log (
    -- 主键: 日志 ID
    sys_job_log_id              NUMERIC(19, 0) NOT NULL,
    -- 日志编号: 非主键, 对外唯一
    sys_job_log_no              VARCHAR(64)    NOT NULL,
    -- 任务 ID
    sys_job_id                  NUMERIC(19, 0),
    -- 任务编号
    sys_job_no                  VARCHAR(64),
    -- 触发应用
    sys_job_log_app             VARCHAR(255),
    -- Handler 名称
    sys_job_log_trigger_bean    VARCHAR(255),
    -- 触发类型
    sys_job_log_trigger_type    VARCHAR(50),
    -- profile
    sys_job_log_profile         VARCHAR(50),
    -- 触发时间
    sys_job_log_trigger_time    TIMESTAMP,
    -- 触发日期
    sys_job_log_trigger_date    DATE,
    -- 触发状态码
    sys_job_log_trigger_code    VARCHAR(20),
    -- 执行状态码
    sys_job_log_execute_code    VARCHAR(20)    DEFAULT 'PADDING',
    -- 耗时: 毫秒
    sys_job_log_cost            DECIMAL(10, 2),
    -- 消息: 统一 VARCHAR(4000) 以兼容 Oracle
    sys_job_log_trigger_msg     VARCHAR(4000),
    -- 参数
    sys_job_log_trigger_param   VARCHAR(512),
    -- 地址
    sys_job_log_trigger_address VARCHAR(255),
    -- 节点 ID
    sys_job_log_node_id         VARCHAR(64),
    -- 日志文件
    sys_job_log_file_path       VARCHAR(500),
    -- 逻辑删除
    sys_job_log_is_deleted      INT            DEFAULT 0,

    -- 通用审计字段 (由 ORM 审计填充, 参考 SysBase)
    create_name                 VARCHAR(64),
    create_by                   INT,
    create_time                 TIMESTAMP,
    update_name                 VARCHAR(64),
    update_by                   INT,
    update_time                 TIMESTAMP,

    PRIMARY KEY (sys_job_log_id),
    CONSTRAINT uk_sys_job_log_no UNIQUE (sys_job_log_no),
    KEY `idx_sys_job_log_job_id` (`sys_job_id`),
    KEY `idx_sys_job_log_node_id` (`sys_job_log_node_id`),
    KEY `idx_sys_job_log_trigger_date` (`sys_job_log_trigger_date`)
);

-- 任务 ID 索引: 用于按任务查询日志
-- 节点 ID 索引: 用于按执行节点查询日志
-- 触发日期索引: 用于按日期统计日志


-- =====================================================
-- 3. 集群选主表 (sys_job_cluster)
-- 用途: 记录各命名空间的 Leader 节点与租约状态
-- 注意: 本表仅有结构, 无初始数据; 首次抢占前会 INSERT 命名空间行,
--       该兜底逻辑在 MysqlLeaderElectionResolver 中实现
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_job_cluster (
    -- 主键: 集群命名空间 ID
    sys_job_cluster_id              VARCHAR(64)    NOT NULL,
    -- 当前 Leader 节点 ID
    sys_job_cluster_leader_node     VARCHAR(64),
    -- 租约到期时间戳 (毫秒)
    sys_job_cluster_lease_until_ms  NUMERIC(19, 0) DEFAULT 0,
    -- 乐观锁版本
    sys_job_cluster_version         NUMERIC(19, 0) DEFAULT 0,

    -- 通用审计字段 (由 ORM 审计填充, 参考 SysBase)
    create_name                     VARCHAR(64),
    create_by                       INT,
    create_time                     TIMESTAMP,
    update_name                     VARCHAR(64),
    update_by                       INT,
    update_time                     TIMESTAMP,

    PRIMARY KEY (sys_job_cluster_id)
);
