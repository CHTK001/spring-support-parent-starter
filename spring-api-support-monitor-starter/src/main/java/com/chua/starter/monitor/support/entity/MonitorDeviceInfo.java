package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备基本信息
 * @author CH
 * @since 2024/12/19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("monitor_device_info")
public class MonitorDeviceInfo {

    /**
     * 主键ID
     */
    @TableId(value = "monitor_device_info_id", type = IdType.AUTO)
    private Long monitorDeviceInfoId;

    /**
     * 设备ID（唯一标识）
     */
    @TableField("monitor_device_info_device_id")
    private String monitorDeviceInfoDeviceId;

    /**
     * 设备名称
     */
    @TableField("monitor_device_info_device_name")
    private String monitorDeviceInfoDeviceName;

    /**
     * 设备IP地址
     */
    @TableField("monitor_device_info_ip_address")
    private String monitorDeviceInfoIpAddress;

    /**
     * 设备端口
     */
    @TableField("monitor_device_info_port")
    private Integer monitorDeviceInfoPort;

    /**
     * 操作系统名称
     */
    @TableField("monitor_device_info_os_name")
    private String monitorDeviceInfoOsName;

    /**
     * 操作系统版本
     */
    @TableField("monitor_device_info_os_version")
    private String monitorDeviceInfoOsVersion;

    /**
     * 系统架构
     */
    @TableField("monitor_device_info_os_arch")
    private String monitorDeviceInfoOsArch;

    /**
     * 主机名
     */
    @TableField("monitor_device_info_hostname")
    private String monitorDeviceInfoHostname;

    /**
     * CPU核心数
     */
    @TableField("monitor_device_info_cpu_cores")
    private Integer monitorDeviceInfoCpuCores;

    /**
     * CPU频率 (MHz)
     */
    @TableField("monitor_device_info_cpu_frequency")
    private Long monitorDeviceInfoCpuFrequency;

    /**
     * 总内存 (bytes)
     */
    @TableField("monitor_device_info_total_memory")
    private Long monitorDeviceInfoTotalMemory;

    /**
     * 总磁盘空间 (bytes)
     */
    @TableField("monitor_device_info_total_disk")
    private Long monitorDeviceInfoTotalDisk;

    /**
     * 设备状态（0-离线，1-在线，2-异常）
     */
    @TableField("monitor_device_info_status")
    private Integer monitorDeviceInfoStatus;

    /**
     * 是否启用监控
     */
    @TableField("monitor_device_info_monitor_enabled")
    private Boolean monitorDeviceInfoMonitorEnabled;

    /**
     * 最后在线时间
     */
    @TableField("monitor_device_info_last_online_time")
    private LocalDateTime monitorDeviceInfoLastOnlineTime;

    /**
     * 最后离线时间
     */
    @TableField("monitor_device_info_last_offline_time")
    private LocalDateTime monitorDeviceInfoLastOfflineTime;

    /**
     * 最后数据上报时间
     */
    @TableField("monitor_device_info_last_report_time")
    private LocalDateTime monitorDeviceInfoLastReportTime;

    /**
     * 扩展属性
     */
    @TableField("monitor_device_info_extra_data")
    private String monitorDeviceInfoExtraData;

    /**
     * 创建时间
     */
    @TableField("monitor_device_info_create_time")
    private LocalDateTime monitorDeviceInfoCreateTime;

    /**
     * 更新时间
     */
    @TableField("monitor_device_info_update_time")
    private LocalDateTime monitorDeviceInfoUpdateTime;
}
