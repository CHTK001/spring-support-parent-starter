package com.chua.starter.datasourcehub.support.model;

/**
 * 拓扑边（一次调用关系）。
 *
 * @param id          边 ID
 * @param source      源节点 ID
 * @param target      目标节点 ID
 * @param detectPoint 探测点（client / server / proxy）
 * @param calls       窗口内调用量（不支持时 -1）
 * @param errorRate   错误率百分比（不支持时 -1）
 * @param avgMs       平均耗时（不支持时 -1）
 * @author CH
 * @since 4.0.0.51
 */
public record TopologyCallVO(String id,
                             String source,
                             String target,
                             String detectPoint,
                             long calls,
                             double errorRate,
                             double avgMs) {
}
