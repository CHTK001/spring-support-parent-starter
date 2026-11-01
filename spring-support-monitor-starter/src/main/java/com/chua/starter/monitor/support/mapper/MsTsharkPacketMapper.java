package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.model.PacketRestoreFrame;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 按会话 + 帧号列表有选填义的回写（-T json 通道的回填用）。
     *
     * <p>一批帧号一起写， {@code CASE WHEN} 结构，SQLite 里 {@code IN} 子句不会超过
     * 驱动上限（规约 32767，这里每批 250），每批只要一次往返。</p>
     *
     * @param sessionId 会话标识
     * @param frames    待回填的信息
     * @return 更新的行数
     */
    int updateBatchRawRestored(@Param("sessionId") Long sessionId,
                               @Param("frames") List<PacketRestoreFrame> frames);

    /**
     * 按会话聚合协议统计（SQL 层聚合，不再全量加载报文）。
     *
     * @param sessionId 会话标识
     * @return 列表，每项含 protocol/count/bytes
     */
    List<Map<String, Object>> selectProtocolStatsBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 按四元组聚合 Top 流（SQL 层聚合，不再全量加载报文）。
     *
     * @param sessionId 会话标识
     * @return 列表，每项含 flow/count/bytes
     */
    List<Map<String, Object>> selectTopFlowsBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 按时间桶聚合报文（用于时间轴统计）。
     *
     * @param sessionId     会话标识
     * @param bucketSeconds 桶大小（秒）
     * @return 列表，每项含 t/packets/bytes
     */
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
    Map<String, Object> selectAnomalyCountsBySessionId(@Param("sessionId") Long sessionId);
}
