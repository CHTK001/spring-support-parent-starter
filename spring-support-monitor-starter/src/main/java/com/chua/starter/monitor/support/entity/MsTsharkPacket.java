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
}
