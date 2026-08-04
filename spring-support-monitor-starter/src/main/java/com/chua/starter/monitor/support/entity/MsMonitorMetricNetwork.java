package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 监控网络明细
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "监控网络明细")
@TableName("ms_monitor_metric_network")
public class MsMonitorMetricNetwork implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 监控主机 id
     */
    @TableField("monitor_id")
    @Schema(description = "监控主机 id", example = "1")
    private Long monitorId;

    /**
     * 网卡名
     */
    @TableField("interface_name")
    @Schema(description = "网卡名", example = "eth0")
    private String interfaceName;

    /**
     * 接收字节数
     */
    @TableField("rx_bytes")
    @Schema(description = "接收字节数", example = "1073741824")
    private Long rxBytes;

    /**
     * 发送字节数
     */
    @TableField("tx_bytes")
    @Schema(description = "发送字节数", example = "536870912")
    private Long txBytes;

    /**
     * 网卡速率(bps)
     */
    @TableField("speed_bps")
    @Schema(description = "网卡速率(bps)", example = "1000000000")
    private Long speedBps;

    /**
     * 采集时间
     */
    @TableField("collect_time")
    @Schema(description = "采集时间", example = "2026-08-04T10:00:00")
    private LocalDateTime collectTime;
}