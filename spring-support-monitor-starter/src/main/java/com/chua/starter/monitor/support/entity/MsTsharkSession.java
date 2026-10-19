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
    @Schema(description = "状态: 0已停止 1抓包中 2异常", example = "1")
    private Integer captureStatus; // capture状态

    /**
     * 异常原因 / 收尾说明。
     *
     * <p>进程异常退出、服务重启导致中断、达到大小上限自动停止等都落在这里，页面在
     * 状态标签上以提示形式展示。正常结束为空。</p>
     */
    @TableField("ms_tshark_session_last_error")
    @Schema(description = "异常原因或停止说明")
    private String lastError; // 最后错误

    /**
     * pcap 文件大小（字节）。
     *
     * <p>用于会话列表展示与「磁盘保护」的可观测性：实时抓包在收尾与增量解析时回填，
     * 导入会话在导入完成时落盘。</p>
     */
    @TableField("ms_tshark_session_file_size")
    @Schema(description = "pcap 文件大小(字节)", example = "1048576")
    private Long fileSize; // 文件大小

    /**
     * 会话大小上限（MB），0 或空表示不限。
     *
     * <p>实时抓包会翻译成 tshark 的 {@code -a filesize:<KB>} 自动停止条件。</p>
     */
    @TableField("ms_tshark_session_max_size")
    @Schema(description = "会话大小上限(MB)，0=不限", example = "512")
    private Integer maxSizeMb; // 大小上限

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
