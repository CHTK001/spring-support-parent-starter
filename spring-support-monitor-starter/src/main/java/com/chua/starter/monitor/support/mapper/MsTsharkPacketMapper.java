package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据包 映射器
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface MsTsharkPacketMapper extends BaseMapper<MsTsharkPacket> {

    /**
     * 按会话聚合协议统计（SQL 层聚合，不再全量加载报文）。
     *
     * @param sessionId 会话标识
     * @return 列表，每项含 protocol/count/bytes
     */
    @Select("SELECT COALESCE(ms_tshark_packet_protocol, 'OTHER') AS protocol, " +
            "COUNT(*) AS count, COALESCE(SUM(ms_tshark_packet_length), 0) AS bytes " +
            "FROM ms_tshark_packet WHERE ms_tshark_packet_session_id = #{sessionId} " +
            "GROUP BY COALESCE(ms_tshark_packet_protocol, 'OTHER') ORDER BY count DESC")
    List<Map<String, Object>> selectProtocolStatsBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 按四元组聚合 Top 流（SQL 层聚合，不再全量加载报文）。
     *
     * @param sessionId 会话标识
     * @return 列表，每项含 flow/count/bytes
     */
    @Select("SELECT CONCAT(COALESCE(ms_tshark_packet_source_ip, ''), ':', COALESCE(ms_tshark_packet_source_port, ''), " +
            "' -> ', COALESCE(ms_tshark_packet_dest_ip, ''), ':', COALESCE(ms_tshark_packet_dest_port, '')) AS flow, " +
            "COUNT(*) AS count, COALESCE(SUM(ms_tshark_packet_length), 0) AS bytes, " +
            "ms_tshark_packet_source_ip AS sourceIp, ms_tshark_packet_source_port AS sourcePort, " +
            "ms_tshark_packet_dest_ip AS destIp, ms_tshark_packet_dest_port AS destPort " +
            "FROM ms_tshark_packet WHERE ms_tshark_packet_session_id = #{sessionId} " +
            "GROUP BY ms_tshark_packet_source_ip, ms_tshark_packet_source_port, " +
            "ms_tshark_packet_dest_ip, ms_tshark_packet_dest_port ORDER BY count DESC LIMIT 20")
    List<Map<String, Object>> selectTopFlowsBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 按时间桶聚合报文（用于时间轴统计）。
     *
     * @param sessionId     会话标识
     * @param bucketSeconds 桶大小（秒）
     * @return 列表，每项含 t/packets/bytes
     */
    @Select("SELECT FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(ms_tshark_packet_time)/#{bucketSeconds})*#{bucketSeconds}) AS t, " +
            "COUNT(*) AS packets, COALESCE(SUM(ms_tshark_packet_length), 0) AS bytes " +
            "FROM ms_tshark_packet WHERE ms_tshark_packet_session_id = #{sessionId} " +
            "GROUP BY t ORDER BY t")
    List<Map<String, Object>> selectTimelineBySessionId(@Param("sessionId") Long sessionId,
                                                        @Param("bucketSeconds") Integer bucketSeconds);

    /**
     * 常见异常诊断计数（SQL 层一次扫描聚合成单行，各列含义见 {@code TsharkAnomalyVO}）。
     *
     * <p>命中条件都挂在 {@code ms_tshark_packet_info}（tshark Info 列）与协议列上：
     * TCP 重传 / 乱序 / 零窗口 / 连接重置在 Info 里有固定的方括号标记
     * （形如 {@code [TCP Retransmission]}、{@code [RST, ACK]}），ICMP 不可达有
     * {@code unreachable} 字样，DNS 失败带 {@code No such name / NXDOMAIN}，HTTP
     * 4xx/5xx 的响应行以 {@code HTTP/x.y 4/5} 开头，TLS 告警行含 {@code Alert}。
     * 与展示层点一行筛报文使用的关键字口径一致，少做一层「前端再算一遍」。</p>
     *
     * @param sessionId 会话标识
     * @return 单行聚合计数；会话没有报文时各列为 null（由服务层按 0 处理）
     */
    @Select("SELECT " +
            "SUM(CASE WHEN ms_tshark_packet_info LIKE '%[RST%' THEN 1 ELSE 0 END) AS reset," +
            "SUM(CASE WHEN ms_tshark_packet_protocol LIKE 'ICMP%' AND ms_tshark_packet_info LIKE '%unreachable%' THEN 1 ELSE 0 END) AS unreachable," +
            "SUM(CASE WHEN ms_tshark_packet_info LIKE '%[TCP Retransmission]%' THEN 1 ELSE 0 END) AS retransmission," +
            "SUM(CASE WHEN ms_tshark_packet_info LIKE '%[TCP Out-Of-Order]%' OR ms_tshark_packet_info LIKE '%[TCP Out-of-Order]%' THEN 1 ELSE 0 END) AS outOfOrder," +
            "SUM(CASE WHEN ms_tshark_packet_info LIKE '%[TCP ZeroWindow]%' THEN 1 ELSE 0 END) AS zeroWindow," +
            "SUM(CASE WHEN ms_tshark_packet_protocol LIKE 'DNS%' AND (ms_tshark_packet_info LIKE '%No such name%' OR ms_tshark_packet_info LIKE '%NXDOMAIN%' OR ms_tshark_packet_info LIKE '%Non-Existent%') THEN 1 ELSE 0 END) AS dnsFail," +
            "SUM(CASE WHEN ms_tshark_packet_info LIKE 'HTTP/1.1 4%' OR ms_tshark_packet_info LIKE 'HTTP/1.0 4%' OR ms_tshark_packet_info LIKE 'HTTP/2 4%' THEN 1 ELSE 0 END) AS http4xx," +
            "SUM(CASE WHEN ms_tshark_packet_info LIKE 'HTTP/1.1 5%' OR ms_tshark_packet_info LIKE 'HTTP/1.0 5%' OR ms_tshark_packet_info LIKE 'HTTP/2 5%' THEN 1 ELSE 0 END) AS http5xx," +
            "SUM(CASE WHEN ms_tshark_packet_protocol LIKE 'TLS%' AND ms_tshark_packet_info LIKE '%Alert%' THEN 1 ELSE 0 END) AS tlsAlert " +
            "FROM ms_tshark_packet WHERE ms_tshark_packet_session_id = #{sessionId}")
    Map<String, Object> selectAnomalyCountsBySessionId(@Param("sessionId") Long sessionId);
}
