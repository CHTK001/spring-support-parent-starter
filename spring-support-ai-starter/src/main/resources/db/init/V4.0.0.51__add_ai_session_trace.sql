-- =====================================================================================
-- V4.0.0.51 会话执行轨迹表
--
-- 【为什么需要】
--   Agent 执行过程中的思考、模型调用、工具调用等可观测信息，此前只经
--   SSE / WebSocket 实时转发给前端，进程一关、页面一刷就没了；
--   工具类信息还被顺手塞进助手消息的 confirmation 字段里，与真正的执行过程混在一起。
--   会话顶部的「轨迹」页要画带时长的甘特图，就必须有可回放的时序数据，故独立建表。
--
-- 【设计：点事件 + 可选区间】
--   一行 = 一条 AgentTrace（点事件）。若该 trace 属于某个区间，
--   带上 span_id，区间结束的那条额外置 span_end=1 并写 span_end_time，
--   前端按 span_id 把开始/结束配成一条带时长的横条。
--   没有配对结束事件的（如仅有一方的工具调用）保持 span_end=0，
--   前端退化为点事件渲染——这样对「只会发点事件」的 Agent 也成立。
--   时长不单独存：它等于 span_end_time - event_time，存了就是两份真相。
--
-- 【幂等】
--   CREATE TABLE IF NOT EXISTS，重复执行不报错也不改动已有表；本脚本不含 INSERT。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_session_trace` (
    `sys_ai_session_trace_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sys_ai_session_trace_uid`  VARCHAR(64)  NOT NULL COMMENT '轨迹记录UID(UUID)',
    `sys_ai_session_id`         BIGINT       DEFAULT NULL COMMENT '所属会话ID，对应 sys_ai_session 主键',
    `chat_request_id`           VARCHAR(64)  DEFAULT NULL COMMENT '本次运行的请求ID：把一次运行的所有轨迹归为一组',
    `kind`                      VARCHAR(32)  NOT NULL COMMENT '类别：agent_start/agent_end/turn/thinking/text/tool_call/tool_args/tool_result/usage/hint/edge',
    `agent_id`                  VARCHAR(128) DEFAULT NULL COMMENT 'Agent 标识',
    `span_id`                   VARCHAR(191) DEFAULT NULL COMMENT '所属区间标识；为空表示这是一条独立的点事件',
    `span_end`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否区间结束标记：1 是，0 否',
    `tool_name`                 VARCHAR(128) DEFAULT NULL COMMENT '工具名称（工具相关轨迹）',
    `text`                      TEXT         DEFAULT NULL COMMENT '轨迹文本',
    `event_time`                BIGINT       NOT NULL COMMENT '事件发生时间戳（毫秒）',
    `span_end_time`             BIGINT       NOT NULL DEFAULT 0 COMMENT '区间结束时间戳（毫秒）；0 表示点事件',
    `seq`                       INT          NOT NULL DEFAULT 0 COMMENT '同一次运行内的递增序号：保证同一毫秒内的多条事件顺序稳定',
    `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人',
    `create_by`    INT          DEFAULT NULL COMMENT '创建人ID',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人',
    `update_by`    INT          DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_ai_session_trace_id`),
    UNIQUE KEY `sys_ai_session_trace_uid` (`sys_ai_session_trace_uid`),
    -- 按会话回放轨迹的主查询路径
    KEY `sys_ai_session_trace_session` (`sys_ai_session_id`, `seq`),
    -- 按一次运行归组
    KEY `sys_ai_session_trace_request` (`chat_request_id`),
    -- 按区间配对开始/结束
    KEY `sys_ai_session_trace_span` (`span_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话执行轨迹（点事件 + 可选区间，供甘特图回放）';
