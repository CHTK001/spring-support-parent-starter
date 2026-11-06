package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 网络数据包
 * @author CH
 * @since 4.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "网络数据包")
@TableName("ms_tshark_packet")
public class MsTsharkPacket extends SysBase {

    private static final long serialVersionUID = 1L; // 串行版本uid

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    @TableField("ms_tshark_packet_session_id")
    @Schema(description = "所属抓包会话 id", example = "1")
    private Long sessionId; // 会话标识

    @TableField("ms_tshark_packet_time")
    @Schema(description = "数据包时间戳", example = "2026-08-30T10:00:01.123")
    private LocalDateTime packetTime; // 数据包时间

    /**
     * tshark 帧号（{@code frame.number}）。
     *
     * <p>帧号是 pcap 内的自然序，用于增量解析游标，也让「显示过滤」里的
     * {@code frame.number} 能直接映射到一条 SQL 条件。</p>
     */
    @TableField("ms_tshark_packet_frame_no")
    @Schema(description = "tshark 帧号", example = "12")
    private Long frameNo; // 帧号

    @TableField("ms_tshark_packet_source_ip")
    @Schema(description = "源IP", example = "10.0.0.3")
    private String sourceIp; // 源ip

    @TableField("ms_tshark_packet_dest_ip")
    @Schema(description = "目的IP", example = "10.0.0.1")
    private String destIp; // destip

    @TableField("ms_tshark_packet_source_port")
    @Schema(description = "源端口", example = "54321")
    private Integer sourcePort; // 源端口

    @TableField("ms_tshark_packet_dest_port")
    @Schema(description = "目的端口", example = "8080")
    private Integer destPort; // dest端口

    @TableField("ms_tshark_packet_protocol")
    @Schema(description = "协议", example = "TCP")
    private String protocol; // 协议

    @TableField("ms_tshark_packet_length")
    @Schema(description = "数据包长度(字节)", example = "124")
    private Integer packetLength; // 数据包长度

    @TableField("ms_tshark_packet_info")
    @Schema(description = "人可读摘要", example = "GET /api HTTP/1.1")
    private String packetInfo; // 数据包信息

    @TableField("ms_tshark_packet_raw")
    @Schema(description = "原始TShark JSON")
    private String rawData; // raw数据

    @TableField("ms_tshark_packet_restored")
    @Schema(description = "协议还原文本")
    private String restoredText; // restored文本

    /**
     * HTTP 请求头（起始行 + 逐行头字段）。
     *
     * <p>解析期由 {@code -T fields} 的 {@code http.request.*} 列拼好入库：详情弹层只要
     * 几行头，打开时再回头扫几百 MB 的 pcap 不划算。</p>
     */
    @TableField("ms_tshark_packet_http_req")
    @Schema(description = "HTTP 请求头")
    private String httpRequestHead; // HTTP 请求头

    /**
     * HTTP 响应头（状态行 + 逐行头字段）。
     */
    @TableField("ms_tshark_packet_http_resp")
    @Schema(description = "HTTP 响应头")
    private String httpResponseHead; // HTTP 响应头

    /**
     * HTTP 实体正文（已解码成文本；二进制以 `HEX ` 前缀给出十六进制预览）。
     *
     * <p>一个帧只有一个方向，所以正文跟随该帧是请求还是响应；前端按有没有
     * 请求头 / 响应头把它归到对应一侧。解析期截断到固定长度，避免一个几 MB
     * 的下载把 SQLite 行撑爆。</p>
     */
    @TableField("ms_tshark_packet_http_body")
    @Schema(description = "HTTP 正文")
    private String httpBody; // HTTP 正文

    /** HTTP 实体类型，如 text/html / application/json */
    @TableField("ms_tshark_packet_http_type")
    @Schema(description = "HTTP 内容类型")
    private String httpContentType; // HTTP 内容类型

    /** HTTP 内容编码，如 gzip / br；解码后仍保留原值供核对 */
    @TableField("ms_tshark_packet_http_encoding")
    @Schema(description = "HTTP 内容编码")
    private String httpContentEncoding; // HTTP 内容编码

    /** HTTP 实体原始字节数（不是截断后的字符数） */
    @TableField("ms_tshark_packet_http_size")
    @Schema(description = "HTTP 正文长度(字节)")
    private Integer httpContentLength; // HTTP 正文长度
}
