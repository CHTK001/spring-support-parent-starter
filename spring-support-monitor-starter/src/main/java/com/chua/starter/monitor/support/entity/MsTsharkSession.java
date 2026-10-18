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
 * 网络抓包会话
 * @author CH
 * @since 4.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "网络抓包会话")
@TableName("ms_tshark_session")
public class MsTsharkSession extends SysBase {

    private static final long serialVersionUID = 1L; // 串行版本uid

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    @TableField("ms_tshark_session_name")
    @Schema(description = "会话名称", example = "eth0 临时抓包")
    private String sessionName; // 会话名称

    @TableField("ms_tshark_session_interface")
    @Schema(description = "抓包网卡", example = "eth0")
    private String captureInterface; // capture接口

    @TableField("ms_tshark_session_filter")
    @Schema(description = "抓包过滤表达式(BPF)", example = "tcp port 8080")
    private String captureFilter; // capture过滤器

    /**
     * 会话来源：{@code live} 网卡实时抓包 / {@code import} 导入的 pcap。
     *
     * <p>两类会话共用同一张表与同一套解析、统计、下载接口，靠本字段区分入口：
     * 「抓包分析」页只列 live，「PCAP 分析」页只列 import，否则两个页面会互相看到对方的数据。</p>
     */
    @TableField("ms_tshark_session_source")
    @Schema(description = "来源: live 实时抓包 / import 导入 pcap", example = "live")
    private String source; // 来源

    @TableField("ms_tshark_session_status")
    @Schema(description = "状态: 0已停止 1抓包中", example = "1")
    private Integer captureStatus; // capture状态

    @TableField("ms_tshark_session_packet_count")
    @Schema(description = "抓取数据包总数", example = "1024")
    private Integer packetCount; // 数据包数量

    @TableField("ms_tshark_session_file_path")
    @Schema(description = "pcap 文件路径")
    private String filePath; // 文件路径

    @TableField("ms_tshark_session_duration")
    @Schema(description = "抓包时长(秒)", example = "30")
    private Integer captureDuration; // capture持续时间

    @TableField("ms_tshark_session_start_time")
    @Schema(description = "开始时间", example = "2026-08-30T10:00:00")
    private LocalDateTime startTime; // 启动时间

    @TableField("ms_tshark_session_end_time")
    @Schema(description = "结束时间", example = "2026-08-30T10:00:30")
    private LocalDateTime endTime; // 结束时间
}
