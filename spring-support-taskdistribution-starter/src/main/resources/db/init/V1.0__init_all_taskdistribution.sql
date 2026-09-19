-- =====================================================
-- 任务分发模块 建表脚本（MySQL / H2 通用子集）
-- 说明: DbTaskStore 以 sys_task_store 落库任务与结果 JSON，
--       此前全仓无对应建表脚本，启用 DB 存储模式即报表不存在。
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_task_store` (
    `task_id`       VARCHAR(128) NOT NULL              COMMENT '任务标识（主键）',
    `task_type`     VARCHAR(128) DEFAULT NULL          COMMENT '任务类型',
    `task_status`   VARCHAR(64)  DEFAULT NULL          COMMENT '任务状态',
    `task_json`     TEXT         DEFAULT NULL          COMMENT '任务JSON（含payload）',
    `result_json`   TEXT         DEFAULT NULL          COMMENT '结果JSON',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`task_id`),
    KEY `sys_task_store_status` (`task_status`),
    KEY `sys_task_store_type` (`task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务存储表';
