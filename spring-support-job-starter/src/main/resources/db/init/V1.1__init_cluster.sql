/*
  V1.1__init_cluster.sql

  跨数据库通用初始化 SQL (MySQL 8 / PostgreSQL 12 / Oracle 12c)
  详见 V1.0__init_job.sql 说明。
*/

CREATE TABLE sys_job_cluster (
    sys_job_cluster_id              VARCHAR(64)  NOT NULL       COMMENT '集群命名空间 ID',
    sys_job_cluster_leader_node     VARCHAR(64)                 COMMENT '当前 Leader 节点 ID',
    sys_job_cluster_lease_until_ms  BIGINT       DEFAULT 0      COMMENT '租约到期时间戳 (毫秒)',
    sys_job_cluster_version         BIGINT       DEFAULT 0      COMMENT '乐观锁版本',
    create_name                     VARCHAR(64)                 COMMENT '创建人姓名',
    create_by                       INT                         COMMENT '创建人',
    create_time                     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                     VARCHAR(64)                 COMMENT '更新人姓名',
    update_by                       INT                         COMMENT '更新人',
    update_time                     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (sys_job_cluster_id)
);

COMMENT ON TABLE sys_job_cluster IS '集群选主表';
