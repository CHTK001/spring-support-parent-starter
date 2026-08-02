package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备信息实体
 * <p>对应数据库表 device_info。</p>
 *
 * <h3>设备大类</h3>
 * <ul>
 *   <li>{@code camera} 摄像头</li>
 *   <li>{@code accessControl} 门禁</li>
 *   <li>{@code alarm} 报警</li>
 *   <li>{@code intercom} 对讲</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("device_info")
public class DeviceInfo {

    /**
     * 主键 ID
     */
    @TableId(value = "device_info_id", type = IdType.ASSIGN_UUID)
    private String deviceInfoId;

    /**
     * 所属组织 ID
     */
    @TableField("device_info_org_id")
    private String deviceInfoOrgId;

    /**
     * 设备名称
     */
    @TableField("device_info_name")
    private String deviceInfoName;

    /**
     * 厂商标识，例如 hikvision、dahua
     */
    @TableField("device_info_vendor")
    private String deviceInfoVendor;

    /**
     * 设备型号
     */
    @TableField("device_info_model")
    private String deviceInfoModel;

    /**
     * 设备大类
     * <p>枚举值：camera（摄像头）、accessControl（门禁）、alarm（报警）、intercom（对讲）</p>
     */
    @TableField("device_info_category")
    private String deviceInfoCategory;

    /**
     * 设备小类（子类）
     * <p>由前端写死枚举值，例如 bullet、dome、ptz、cardReader、fingerprint、panicButton 等。</p>
     */
    @TableField("device_info_sub_category")
    private String deviceInfoSubCategory;

    /**
     * IP 地址
     */
    @TableField("device_info_ip")
    private String deviceInfoIp;

    /**
     * 端口
     */
    @TableField("device_info_port")
    private Integer deviceInfoPort;

    /**
     * 用户名
     */
    @TableField("device_info_username")
    private String deviceInfoUsername;

    /**
     * 密码（加密存储）
     */
    @TableField("device_info_password")
    private String deviceInfoPassword;

    /**
     * 是否在线
     */
    @TableField("device_info_online")
    private Boolean deviceInfoOnline;

    /**
     * 设备序列号
     */
    @TableField("device_info_serial")
    private String deviceInfoSerial;

    /**
     * 固件版本
     */
    @TableField("device_info_firmware")
    private String deviceInfoFirmware;

    /**
     * 通道数量
     */
    @TableField("device_info_channel_count")
    private Integer deviceInfoChannelCount;

    /**
     * 纬度（地图位置）
     */
    @TableField("device_info_latitude")
    private BigDecimal deviceInfoLatitude;

    /**
     * 经度（地图位置）
     */
    @TableField("device_info_longitude")
    private BigDecimal deviceInfoLongitude;

    /**
     * 详细地址
     */
    @TableField("device_info_address")
    private String deviceInfoAddress;

    /**
     * 离线检测阈值（秒），超过该秒数未收到心跳视为离线
     */
    @TableField("device_info_offline_threshold")
    private Integer deviceInfoOfflineThreshold;

    /**
     * 创建时间
     */
    @TableField("device_info_created_at")
    private LocalDateTime deviceInfoCreatedAt;

    /**
     * 更新时间
     */
    @TableField("device_info_updated_at")
    private LocalDateTime deviceInfoUpdatedAt;
}