package com.chua.starter.monitor.support.model;

/**
 * 抓包会话 Top 流行
 *
 * @param flow  流标识，形如 {@code srcIp:srcPort -> dstIp:dstPort}
 * @param count 该流报文数
 * @param bytes 该流字节数合计
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkFlowVO(String flow, int count, long bytes) {
}
