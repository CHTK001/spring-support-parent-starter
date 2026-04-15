-- ============================================================
-- spider_task：爬虫任务定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_task (
    id              BIGINT          NOT NULL COMMENT '主键（雪花ID）',
    task_code       VARCHAR(64)     NOT NULL COMMENT '任务唯一编码',
    task_name       VARCHAR(128)    NOT NULL COMMENT '任务名称',
    entry_url       VARCHAR(2048)   NOT NULL COMMENT '入口 URL / Seed',
    description     VARCHAR(512)    NULL     COMMENT '任务说明',
    tags            VARCHAR(256)    NULL     COMMENT '任务标签（逗号分隔）',
    auth_type       VARCHAR(32)     NULL     COMMENT '认证方式（NONE/BASIC/COOKIE/TOKEN）',
    execution_type  VARCHAR(16)     NOT NULL DEFAULT 'ONCE' COMMENT '执行类型（ONCE/REPEAT_N/SCHEDULED）',
    execution_policy TEXT           NULL     COMMENT '执行策略 JSON',
    ai_profile      TEXT           NULL     COMMENT 'AI 配置 JSON',
    credential_ref  TEXT           NULL     COMMENT '凭证引用 JSON（不含明文密码）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY idx_status (status),
    KEY idx_execution_type (execution_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫任务定义';

-- ============================================================
-- spider_flow：爬虫编排定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_flow (
    id          BIGINT  NOT NULL COMMENT '主键（雪花ID）',
    task_id     BIGINT  NOT NULL COMMENT '关联任务 ID',
    nodes_json  LONGTEXT NULL    COMMENT '节点列表 JSON',
    edges_json  LONGTEXT NULL    COMMENT '有向边列表 JSON',
    version     INT     NOT NULL DEFAULT 0 COMMENT '编排版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫编排定义';

-- ============================================================
-- spider_job_binding：任务与 job-starter 调度绑定表
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_job_binding (
    id              BIGINT      NOT NULL COMMENT '主键（雪花ID）',
    task_id         BIGINT      NOT NULL COMMENT '关联任务 ID',
    job_binding_id  VARCHAR(128) NOT NULL COMMENT 'job-starter 返回的调度绑定 ID',
    job_channel     VARCHAR(64)  NULL    COMMENT '调度通道',
    active          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否有效',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_job_binding_id (job_binding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务调度绑定';

-- ============================================================
-- spider_runtime_snapshot：任务运行时快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_runtime_snapshot (
    id                  BIGINT      NOT NULL COMMENT '主键（雪花ID）',
    task_id             BIGINT      NOT NULL COMMENT '关联任务 ID',
    status              VARCHAR(16)  NOT NULL DEFAULT 'READY' COMMENT '当前任务状态',
    last_execute_time   DATETIME     NULL    COMMENT '最近执行时间',
    success_count       BIGINT       NOT NULL DEFAULT 0 COMMENT '成功计数',
    failure_count       BIGINT       NOT NULL DEFAULT 0 COMMENT '失败计数',
    last_error_summary  VARCHAR(1024) NULL   COMMENT '最近错误摘要',
    job_bound           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已绑定 job-starter',
    node_log_summary    LONGTEXT     NULL    COMMENT '节点日志摘要 JSON',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务运行时快照';

-- ============================================================
-- spider_execution_record：单次执行记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_execution_record (
    id              BIGINT      NOT NULL COMMENT '主键（雪花ID）',
    task_id         BIGINT      NOT NULL COMMENT '关联任务 ID',
    execution_type  VARCHAR(16)  NOT NULL COMMENT '执行类型',
    start_time      DATETIME     NULL    COMMENT '开始时间',
    end_time        DATETIME     NULL    COMMENT '结束时间',
    success_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '成功计数',
    failure_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '失败计数',
    trigger_source  VARCHAR(32)  NULL    COMMENT '触发来源（MANUAL/SCHEDULED）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次执行记录';

-- ============================================================
-- spider_workbench_tab：工作台 Tab 持久化表
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_workbench_tab (
    id          BIGINT      NOT NULL COMMENT '主键（雪花ID）',
    tab_type    VARCHAR(16)  NOT NULL DEFAULT 'TASK' COMMENT 'Tab 类型（HOME/TASK）',
    task_id     BIGINT       NULL    COMMENT '关联任务 ID',
    title       VARCHAR(128) NULL    COMMENT 'Tab 标题',
    closeable   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可关闭',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台 Tab';

-- ============================================================
-- spider_credential：凭证池（加密存储，不含明文密码）
-- ============================================================
CREATE TABLE IF NOT EXISTS spider_credential (
    id                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    credential_name   VARCHAR(128) NOT NULL COMMENT '凭证显示名称',
    credential_type   VARCHAR(32)  NOT NULL COMMENT '凭证类型（BASIC/COOKIE/TOKEN/SMS_CODE）',
    encrypted_data    TEXT         NOT NULL COMMENT 'AES 加密后的凭证内容',
    domain            VARCHAR(256) NULL     COMMENT '适用域名（如 gitee.com）',
    description       VARCHAR(512) NULL     COMMENT '备注说明',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_domain (domain),
    KEY idx_type (credential_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证池（加密存储）';
