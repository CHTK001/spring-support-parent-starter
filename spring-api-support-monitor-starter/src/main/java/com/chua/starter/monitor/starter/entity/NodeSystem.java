package com.chua.starter.monitor.starter.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 节点系统表 - 用于记录节点历史数据和统计信息
 *
 * @author CH
 * @since 2024/12/30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_sys_gen_node_system")
@ApiModel(value = "NodeSystem", description = "节点系统信息")
@Schema(description = "节点系统信息")
public class NodeSystem extends SysBase {

    /**
     * 节点ID - MD5(IP+PORT)
     */
    @TableId("node_system_id")
    @ApiModelProperty(value = "节点ID", required = true)
    @Schema(description = "节点ID - MD5(IP+PORT)")
    private String nodeSystemId;

    /**
     * 节点名称
     */
    @TableField("node_system_name")
    @ApiModelProperty(value = "节点名称")
    @Schema(description = "节点名称")
    private String nodeSystemName;

    /**
     * 应用名称
     */
    @TableField("node_system_application_name")
    @ApiModelProperty(value = "应用名称", required = true)
    @Schema(description = "应用名称")
    private String nodeSystemApplicationName;

    /**
     * IP地址
     */
    @TableField("node_system_ip_address")
    @ApiModelProperty(value = "IP地址", required = true)
    @Schema(description = "IP地址")
    private String nodeSystemIpAddress;

    /**
     * 端口号
     */
    @TableField("node_system_port")
    @ApiModelProperty(value = "端口号", required = true)
    @Schema(description = "端口号")
    private Integer nodeSystemPort;

    /**
     * 节点状态：ONLINE-在线, OFFLINE-离线, CONNECTING-连接中, ERROR-异常, MAINTENANCE-维护中
     */
    @TableField("node_system_status")
    @ApiModelProperty(value = "节点状态")
    @Schema(description = "节点状态：ONLINE-在线, OFFLINE-离线, CONNECTING-连接中, ERROR-异常, MAINTENANCE-维护中")
    private String nodeSystemStatus;

    /**
     * 节点类型
     */
    @TableField("node_system_type")
    @ApiModelProperty(value = "节点类型")
    @Schema(description = "节点类型")
    private String nodeSystemType;

    /**
     * 服务版本
     */
    @TableField("node_system_version")
    @ApiModelProperty(value = "服务版本")
    @Schema(description = "服务版本")
    private String nodeSystemVersion;

    /**
     * 是否健康
     */
    @TableField("node_system_healthy")
    @ApiModelProperty(value = "是否健康")
    @Schema(description = "是否健康")
    private Boolean nodeSystemHealthy;

    /**
     * 首次发现时间
     */
    @TableField("node_system_first_discovered_time")
    @ApiModelProperty(value = "首次发现时间")
    @Schema(description = "首次发现时间")
    private LocalDateTime nodeSystemFirstDiscoveredTime;

    /**
     * 最后在线时间
     */
    @TableField("node_system_last_online_time")
    @ApiModelProperty(value = "最后在线时间")
    @Schema(description = "最后在线时间")
    private LocalDateTime nodeSystemLastOnlineTime;

    /**
     * 最后离线时间
     */
    @TableField("node_system_last_offline_time")
    @ApiModelProperty(value = "最后离线时间")
    @Schema(description = "最后离线时间")
    private LocalDateTime nodeSystemLastOfflineTime;

    /**
     * 最后心跳时间
     */
    @TableField("node_system_last_heartbeat_time")
    @ApiModelProperty(value = "最后心跳时间")
    @Schema(description = "最后心跳时间")
    private LocalDateTime nodeSystemLastHeartbeatTime;

    /**
     * 总在线时长（秒）
     */
    @TableField("node_system_total_online_duration")
    @ApiModelProperty(value = "总在线时长（秒）")
    @Schema(description = "总在线时长（秒）")
    private Long nodeSystemTotalOnlineDuration;

    /**
     * 连接次数
     */
    @TableField("node_system_connection_count")
    @ApiModelProperty(value = "连接次数")
    @Schema(description = "连接次数")
    private Integer nodeSystemConnectionCount;

    /**
     * 平均响应时间（毫秒）
     */
    @TableField("node_system_avg_response_time")
    @ApiModelProperty(value = "平均响应时间（毫秒）")
    @Schema(description = "平均响应时间（毫秒）")
    private Double nodeSystemAvgResponseTime;

    /**
     * 最大响应时间（毫秒）
     */
    @TableField("node_system_max_response_time")
    @ApiModelProperty(value = "最大响应时间（毫秒）")
    @Schema(description = "最大响应时间（毫秒）")
    private Long nodeSystemMaxResponseTime;

    /**
     * 最小响应时间（毫秒）
     */
    @TableField("node_system_min_response_time")
    @ApiModelProperty(value = "最小响应时间（毫秒）")
    @Schema(description = "最小响应时间（毫秒）")
    private Long nodeSystemMinResponseTime;

    /**
     * 平均CPU使用率
     */
    @TableField("node_system_avg_cpu_usage")
    @ApiModelProperty(value = "平均CPU使用率")
    @Schema(description = "平均CPU使用率")
    private Double nodeSystemAvgCpuUsage;

    /**
     * 平均内存使用率
     */
    @TableField("node_system_avg_memory_usage")
    @ApiModelProperty(value = "平均内存使用率")
    @Schema(description = "平均内存使用率")
    private Double nodeSystemAvgMemoryUsage;

    /**
     * 平均磁盘使用率
     */
    @TableField("node_system_avg_disk_usage")
    @ApiModelProperty(value = "平均磁盘使用率")
    @Schema(description = "平均磁盘使用率")
    private Double nodeSystemAvgDiskUsage;

    /**
     * 平均网络延迟（毫秒）
     */
    @TableField("node_system_avg_network_latency")
    @ApiModelProperty(value = "平均网络延迟（毫秒）")
    @Schema(description = "平均网络延迟（毫秒）")
    private Double nodeSystemAvgNetworkLatency;

    /**
     * 错误次数
     */
    @TableField("node_system_error_count")
    @ApiModelProperty(value = "错误次数")
    @Schema(description = "错误次数")
    private Integer nodeSystemErrorCount;

    /**
     * 最后错误时间
     */
    @TableField("node_system_last_error_time")
    @ApiModelProperty(value = "最后错误时间")
    @Schema(description = "最后错误时间")
    private LocalDateTime nodeSystemLastErrorTime;

    /**
     * 最后错误信息
     */
    @TableField("node_system_last_error_message")
    @ApiModelProperty(value = "最后错误信息")
    @Schema(description = "最后错误信息")
    private String nodeSystemLastErrorMessage;

    /**
     * 扩展元数据（JSON格式）
     */
    @TableField("node_system_metadata")
    @ApiModelProperty(value = "扩展元数据")
    @Schema(description = "扩展元数据（JSON格式）")
    private String nodeSystemMetadata;

    /**
     * 备注信息
     */
    @TableField("node_system_remark")
    @ApiModelProperty(value = "备注信息")
    @Schema(description = "备注信息")
    private String nodeSystemRemark;

    /**
     * 是否启用监控
     */
    @TableField("node_system_monitor_enabled")
    @ApiModelProperty(value = "是否启用监控")
    @Schema(description = "是否启用监控")
    private Boolean nodeSystemMonitorEnabled;

    /**
     * 监控间隔（秒）
     */
    @TableField("node_system_monitor_interval")
    @ApiModelProperty(value = "监控间隔（秒）")
    @Schema(description = "监控间隔（秒）")
    private Integer nodeSystemMonitorInterval;

    /**
     * 获取节点地址
     */
    public String getAddress() {
        return nodeSystemIpAddress + ":" + nodeSystemPort;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return nodeSystemName != null ? nodeSystemName : nodeSystemApplicationName;
    }

    /**
     * 判断节点是否在线
     */
    public boolean isOnline() {
        return "ONLINE".equals(nodeSystemStatus);
    }

    /**
     * 判断节点是否健康
     */
    public boolean isHealthy() {
        return Boolean.TRUE.equals(nodeSystemHealthy) && isOnline();
    }

    /**
     * 计算在线率（百分比）
     */
    public double getOnlineRate() {
        if (nodeSystemTotalOnlineDuration == null || nodeSystemTotalOnlineDuration == 0) {
            return 0.0;
        }

        LocalDateTime firstTime = nodeSystemFirstDiscoveredTime;
        if (firstTime == null) {
            return 100.0;
        }

        long totalDuration = java.time.Duration.between(firstTime, LocalDateTime.now()).getSeconds();
        if (totalDuration <= 0) {
            return 100.0;
        }

        return Math.min(100.0, (nodeSystemTotalOnlineDuration.doubleValue() / totalDuration) * 100.0);
    }
}
