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
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_info")
public class DeviceInfo {

    /**
     * 主键 标识
     */
    @TableId(value = "device_info_id", type = IdType.ASSIGN_UUID)
    /** Device信息标识 */
    private String deviceInfoId;

    /**
     * 所属组织 标识
     */
    @TableField("device_info_org_id")
    /** Device信息ORGID */
    private String deviceInfoOrgId;

    /**
     * 设备名称
     */
    @TableField("device_info_name")
    /** Device信息名称 */
    private String deviceInfoName;

    /**
     * 厂商标识，例如 hikvision、dahua
     */
    @TableField("device_info_vendor")
    /** Device信息供应商 */
    private String deviceInfoVendor;

    /**
     * 设备型号
     */
    @TableField("device_info_model")
    /** Device信息模型 */
    private String deviceInfoModel;

    /**
     * 设备大类
     * <p>枚举值：camera（摄像头）、accessControl（门禁）、alarm（报警）、intercom（对讲）</p>
     */
    @TableField("device_info_category")
    /** Device信息分类 */
    private String deviceInfoCategory;

    /**
     * 设备小类（子类）
     * <p>由前端写死枚举值，例如 bullet、dome、ptz、cardReader、fingerprint、panicButton 等。</p>
     */
    @TableField("device_info_sub_category")
    /** Device信息SUB分类 */
    private String deviceInfoSubCategory;

    /**
     * IP 地址
     */
    @TableField("device_info_ip")
    /** Device信息IP */
    private String deviceInfoIp;

    /**
     * 端口
     */
    @TableField("device_info_port")
    /** Device信息端口 */
    private Integer deviceInfoPort;

    /**
     * 用户名
     */
    @TableField("device_info_username")
    /** Device信息用户名 */
    private String deviceInfoUsername;

    /**
     * 密码（加密存储）
     */
    @TableField("device_info_password")
    /** Device信息密码 */
    private String deviceInfoPassword;

    /**
     * 是否在线
     */
    @TableField("device_info_online")
    /** Device信息online */
    private Boolean deviceInfoOnline;

    /**
     * 设备序列号
     */
    @TableField("device_info_serial")
    /** Device信息串行 */
    private String deviceInfoSerial;

    /**
     * 固件版本
     */
    @TableField("device_info_firmware")
    /** Device信息firmware */
    private String deviceInfoFirmware;

    /**
     * 通道数量
     */
    @TableField("device_info_channel_count")
    /** Device信息通道数量 */
    private Integer deviceInfoChannelCount;

    /**
     * 纬度（地图位置）
     */
    @TableField("device_info_latitude")
    /** Device信息latitude */
    private BigDecimal deviceInfoLatitude;

    /**
     * 经度（地图位置）
     */
    @TableField("device_info_longitude")
    /** Device信息longitude */
    private BigDecimal deviceInfoLongitude;

    /**
     * 详细地址
     */
    @TableField("device_info_address")
    /** Device信息地址 */
    private String deviceInfoAddress;

    /**
     * 离线检测阈值（秒），超过该秒数未收到心跳视为离线
     */
    @TableField("device_info_offline_threshold")
    /** Device信息offline阈值 */
    private Integer deviceInfoOfflineThreshold;

    /**
     * 创建时间
     */
    @TableField("device_info_created_at")
    /** Device信息创建时间AT */
    private LocalDateTime deviceInfoCreatedAt;

    /**
     * 更新时间
     */
    @TableField("device_info_updated_at")
    /** Device信息更新时间AT */
    private LocalDateTime deviceInfoUpdatedAt;
}
