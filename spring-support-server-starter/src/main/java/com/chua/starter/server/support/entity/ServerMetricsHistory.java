package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务器指标历史")
@TableName("server_metrics_history")
public class ServerMetricsHistory extends SysBase {

    @TableId(value = "server_metrics_history_id", type = IdType.AUTO)
    @ApiModelProperty("服务器指标历史ID")
    private Long serverMetricsHistoryId;

    @TableField("server_id")
    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @TableField("server_code")
    @ApiModelProperty("服务器编码")
    private String serverCode;

    @TableField("server_status")
    @ApiModelProperty("状态")
    private String status;

    @TableField("server_online")
    @ApiModelProperty("是否在线")
    private Boolean online;

    @TableField("server_latency_ms")
    @ApiModelProperty("延迟毫秒")
    private Integer latencyMs;

    @TableField("server_cpu_usage")
    @ApiModelProperty("CPU 使用率")
    private Double cpuUsage;

    @TableField("server_cpu_cores")
    @ApiModelProperty("CPU 核数")
    private Integer cpuCores;

    @TableField("server_memory_usage")
    @ApiModelProperty("内存使用率")
    private Double memoryUsage;

    @TableField("server_memory_total_bytes")
    @ApiModelProperty("内存总量")
    private Long memoryTotalBytes;

    @TableField("server_memory_used_bytes")
    @ApiModelProperty("内存已用")
    private Long memoryUsedBytes;

    @TableField("server_disk_usage")
    @ApiModelProperty("磁盘使用率")
    private Double diskUsage;

    @TableField("server_disk_total_bytes")
    @ApiModelProperty("磁盘总量")
    private Long diskTotalBytes;

    @TableField("server_disk_used_bytes")
    @ApiModelProperty("磁盘已用")
    private Long diskUsedBytes;

    @TableField("server_io_read_bps")
    @ApiModelProperty("读取吞吐")
    private Double ioReadBytesPerSecond;

    @TableField("server_io_write_bps")
    @ApiModelProperty("写入吞吐")
    private Double ioWriteBytesPerSecond;

    @TableField("server_network_rx_pps")
    @ApiModelProperty("接收包速率")
    private Double networkRxPacketsPerSecond;

    @TableField("server_network_tx_pps")
    @ApiModelProperty("发送包速率")
    private Double networkTxPacketsPerSecond;

    @TableField("server_collect_timestamp")
    @ApiModelProperty("采集时间戳")
    private Long collectTimestamp;

    @TableField("server_detail_message")
    @ApiModelProperty("采集说明")
    private String detailMessage;
}
