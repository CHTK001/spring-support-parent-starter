package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * Agent 配置视图：采集间隔与该资产下的采集任务
 *
 * @param collectIntervalMs 采集间隔（毫秒）
 * @param tasks             采集任务列表，永不为 空
 * @author CH
 * @since 4.0.0.42
 */
public record AgentConfigVO(Long collectIntervalMs, List<AgentTaskVO> tasks) {
}
