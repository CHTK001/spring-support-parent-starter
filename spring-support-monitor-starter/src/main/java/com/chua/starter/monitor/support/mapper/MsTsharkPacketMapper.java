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
}
