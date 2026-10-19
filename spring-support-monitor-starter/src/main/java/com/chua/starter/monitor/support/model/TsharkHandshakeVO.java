package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * 一条 TCP 流的握手 / 关闭生命周期。
 *
 * <p>由「点中某个包 → 还原它所属的那条流的完整时序」得到，供抓包页右键查看。</p>
 *
 * @param sessionId  所属抓包会话
 * @param clientIp   主动发起方（发第一个 SYN 的一方）
 * @param clientPort 主动发起方端口
 * @param serverIp   被动方 IP
 * @param serverPort 被动方端口
 * @param handshake  握手判定结果
 * @param steps      按时间排序的报文序列（已解析出标志位与相对耗时）
 * @param totalSteps 完整步骤数（截断前）
 * @param truncated  是否因为步骤过多被截断（只保留首尾与锚点包）
 * @param rttMs      TCP 建连 RTT（SYN → SYN-ACK）毫秒；抓不到返回 null
 * @param ttfbMs     首字节时间（首个 HTTP 请求 → 首个 HTTP 响应）毫秒；无 HTTP 返回 null
 * @author CH
 * @since 4.0.0.42
 */
public record TsharkHandshakeVO(
        Long sessionId,
        String clientIp,
        Integer clientPort,
        String serverIp,
        Integer serverPort,
        Handshake handshake,
        List<Step> steps,
        int totalSteps,
        boolean truncated,
        Long rttMs,
        Long ttfbMs) {

    /**
     * 一条报文。
     *
     * @param packetId  数据包 id
     * @param time      时间戳文本
     * @param fromClient true = 主动方 → 被动方
     * @param flags     解析出的 TCP 标志位，如 {@code SYN,ACK}；非 TCP 为空
     * @param seq       tshark 摘要里的 Seq（可能为空）
     * @param ack       tshark 摘要里的 Ack（可能为空）
     * @param length    报文长度
     * @param info      tshark 人可读摘要
     * @param relativeMs 相对本流第一个包的耗时（毫秒）
     * @param phase     所处阶段：TCP / TLS / HTTP / DATA / CLOSE
     * @param retransmission 是否为重传 / 乱序 / 重复 ACK
     * @param reset     是否带 RST（连接被重置）
     */
    public record Step(Long packetId, String time, boolean fromClient, String flags,
                       String seq, String ack, Integer length, String info, long relativeMs,
                       String phase, boolean retransmission, boolean reset) {
    }

    /**
     * 握手判定。
     *
     * @param kind      {@code THREE_WAY} / {@code FOUR_WAY} / {@code NONE}
     * @param label     中文标签，直接展示
     * @param detail    补充说明（如为什么判定为 4 次）
     * @param completed 是否已建立
     * @param closed    是否已关闭（双向 FIN）
     */
    public record Handshake(String kind, String label, String detail, boolean completed, boolean closed) {
    }
}