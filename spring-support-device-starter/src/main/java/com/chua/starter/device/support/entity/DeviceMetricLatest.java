package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备环境指标最新快照。
 *
 * <p>同一设备、同一节点只保留一行，采集任务每次覆盖；历史趋势不落在本表，
 * 避免气象站按分钟上报把设备库撑大。需要历史时按设备与时间窗从厂商查询。</p>
 *
 * @since 4.0.0.54
 * @author CH
 */
@Data
@TableName("device_metric_latest")
public class DeviceMetricLatest {

    /** 主键 */
    @TableId(value = "device_metric_id", type = IdType.ASSIGN_UUID)
    private String deviceMetricId;

    /** 设备 ID */
    @TableField("device_info_id")
    private String deviceInfoId;

    /** 节点稳定标识（厂商节点 ID，或名称 + 单位） */
    @TableField("device_metric_key")
    private String deviceMetricKey;

    /** 节点名称，例如温度 */
    @TableField("device_metric_name")
    private String deviceMetricName;

    /** 读数；字符串兼容风向、天气现象等非数值指标 */
    @TableField("device_metric_value")
    private String deviceMetricValue;

    /** 单位 */
    @TableField("device_metric_unit")
    private String deviceMetricUnit;

    /** 厂商侧数据时间 */
    @TableField("device_metric_data_time")
    private LocalDateTime deviceMetricDataTime;

    /** 本地采集时间 */
    @TableField("device_metric_collected_at")
    private LocalDateTime deviceMetricCollectedAt;
}
