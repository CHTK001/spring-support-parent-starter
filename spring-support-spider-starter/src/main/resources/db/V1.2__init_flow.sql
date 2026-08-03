-- ============================================
-- Spring Support Spider Starter - 可视化编排
-- 版本: V1.2
-- 说明: 引入"流程定义 + 节点 + 历史版本"三张表，
--        配合 RelationGraph 前端实现类 magic-api 的可视化爬虫编排。
--
-- 设计要点：
--   - spider_flow: 流程定义主表（一个爬虫可有多个版本；当前发布版 = 最新 enabled）
--   - spider_flow_node: 流程下的节点（含类型 + 配置 JSON，与前端 RelationGraph 节点一一对应）
--   - spider_flow_edge: 节点之间的连线（条件流转：condField/condOp/condValue 三元组决定是否走此边）
--   - spider_flow_history: 历史版本（每次保存生成快照，支持回滚）
-- ============================================

CREATE TABLE IF NOT EXISTS spider_flow (
    flow_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流程主键',
    flow_code            VARCHAR(64)  NOT NULL                COMMENT '业务编码，全局唯一',
    flow_name            VARCHAR(128) NOT NULL                COMMENT '流程名称',
    flow_version         INT          NOT NULL DEFAULT 1      COMMENT '当前版本号',
    flow_graph_json      MEDIUMTEXT   NOT NULL               COMMENT 'RelationGraph 完整 JSON（节点 + 边 + 配置）',
    flow_node_count      INT          NOT NULL DEFAULT 0      COMMENT '节点数（冗余，便于列表展示）',
    flow_status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 启用',
    flow_remark          VARCHAR(512) DEFAULT NULL             COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (flow_id),
    UNIQUE KEY uk_spider_flow_code (flow_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='爬虫流程定义';

CREATE TABLE IF NOT EXISTS spider_flow_node (
    flow_node_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点主键',
    flow_id              BIGINT       NOT NULL                COMMENT '所属流程',
    flow_node_key        VARCHAR(64)  NOT NULL                COMMENT '节点唯一 key（前端 RelationGraph 的 node.id）',
    flow_node_type        VARCHAR(32)  NOT NULL                COMMENT '节点类型：start/request/extract/loop/output/script/db/sql/function/stop',
    flow_node_name        VARCHAR(128) NOT NULL                COMMENT '节点显示名',
    flow_node_x           INT          NOT NULL DEFAULT 0      COMMENT '画布 X 坐标',
    flow_node_y           INT          NOT NULL DEFAULT 0      COMMENT '画布 Y 坐标',
    flow_node_config      MEDIUMTEXT   NOT NULL               COMMENT '节点配置 JSON（不同节点类型结构不同）',
    flow_node_status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 启用',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (flow_node_id),
    UNIQUE KEY uk_spider_flow_node (flow_id, flow_node_key),
    INDEX idx_spider_flow_node_flow (flow_id),
    CONSTRAINT fk_spider_flow_node_flow FOREIGN KEY (flow_id) REFERENCES spider_flow (flow_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点';

CREATE TABLE IF NOT EXISTS spider_flow_edge (
    flow_edge_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '边主键',
    flow_id              BIGINT       NOT NULL                COMMENT '所属流程',
    flow_edge_from        VARCHAR(64)  NOT NULL                COMMENT '起点节点 key',
    flow_edge_to          VARCHAR(64)  NOT NULL                COMMENT '终点节点 key',
    flow_edge_text        VARCHAR(128) DEFAULT NULL             COMMENT '连线上的标签',
    flow_edge_priority    INT          DEFAULT 0                COMMENT '条件求值顺序（同 from 下按 priority 升序匹配）',
    flow_edge_cond_field  VARCHAR(64)  DEFAULT NULL             COMMENT '条件：变量名（空 = 无条件 = 必走）',
    flow_edge_cond_op     VARCHAR(16)  DEFAULT NULL             COMMENT '条件：= != > >= < <= contains empty notEmpty',
    flow_edge_cond_value  VARCHAR(256) DEFAULT NULL             COMMENT '条件：比较值',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (flow_edge_id),
    INDEX idx_spider_flow_edge_flow (flow_id),
    INDEX idx_spider_flow_edge_from (flow_id, flow_edge_from),
    CONSTRAINT fk_spider_flow_edge_flow FOREIGN KEY (flow_id) REFERENCES spider_flow (flow_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程边（节点流转）';

CREATE TABLE IF NOT EXISTS spider_flow_history (
    flow_history_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '历史版本主键',
    flow_id              BIGINT       NOT NULL                COMMENT '所属流程',
    flow_version          INT          NOT NULL                COMMENT '版本号',
    flow_graph_json      MEDIUMTEXT   NOT NULL               COMMENT '该版本的完整图 JSON',
    flow_change_note      VARCHAR(512) DEFAULT NULL             COMMENT '版本变更说明',
    flow_change_user      VARCHAR(64)  DEFAULT NULL             COMMENT '修改人',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (flow_history_id),
    UNIQUE KEY uk_spider_flow_history (flow_id, flow_version),
    INDEX idx_spider_flow_history_flow (flow_id),
    CONSTRAINT fk_spider_flow_history_flow FOREIGN KEY (flow_id) REFERENCES spider_flow (flow_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程历史版本（快照）';

CREATE TABLE IF NOT EXISTS spider_flow_node_log (
    flow_node_log_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点日志主键',
    flow_execution_no     VARCHAR(64)  NOT NULL                COMMENT '执行批次号（与 spider_execution_record.spider_execution_no 同源）',
    flow_id              BIGINT       DEFAULT NULL             COMMENT '流程 ID（冗余，便于按流程过滤）',
    flow_node_key        VARCHAR(64)  DEFAULT NULL             COMMENT '执行到的节点 key',
    flow_node_type        VARCHAR(32)  DEFAULT NULL             COMMENT '节点类型',
    flow_node_status      VARCHAR(16)  NOT NULL                COMMENT 'SUCCESS / FAILED / SKIPPED',
    flow_node_message     MEDIUMTEXT   DEFAULT NULL             COMMENT '节点输出 / 异常 / 变量值摘要',
    flow_node_cost_ms     BIGINT       DEFAULT 0               COMMENT '节点耗时（毫秒）',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (flow_node_log_id),
    INDEX idx_spider_flow_node_log_exec (flow_execution_no),
    INDEX idx_spider_flow_node_log_flow (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点执行日志（可视化调试用）';
