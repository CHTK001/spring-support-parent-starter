package com.chua.starter.monitor.support.model;

/**
 * 抓包协议统计行
 *
 * @param protocol 协议名，未识别协议归为 OTHER
 * @param count    该协议报文数
 * @param bytes    该协议字节数合计
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkProtocolStatVO(String protocol, int count, long bytes) {
}
