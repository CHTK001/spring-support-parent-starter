package com.chua.starter.monitor.support.model;

/**
 * 抓包会话 Top 流行
 *
 * @param flow  流标识，形如 {@code srcIp:srcPort -> dstIp:dstPort}
 * @param count 该流报文数
 * @param bytes 该流字节数合计
 * @param sourceIp   源 IP（便于点击后生成精确的显示过滤）
 * @param sourcePort 源端口（可空）
 * @param destIp     目的 IP
 * @param destPort   目的端口（可空）
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkFlowVO(String flow, int count, long bytes,
                           String sourceIp, Integer sourcePort,
                           String destIp, Integer destPort) {
}
