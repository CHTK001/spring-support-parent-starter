package com.chua.starter.monitor.support.node;

/**
 * 在线节点统计。
 *
 * @param total   注册节点总数
 * @param online  在线节点数
 * @param offline 离线节点数
 * @author CH
 * @since 4.0.0.42
 */
public record MonitorNodeStatsVO(int total, long online, long offline) {
}
