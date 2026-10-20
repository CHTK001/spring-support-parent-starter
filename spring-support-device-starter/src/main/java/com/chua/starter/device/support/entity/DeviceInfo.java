package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    /**
     * Device信息标识
    */
    private String deviceInfoId;

    /**
     * 所属组织 标识
     */
    @TableField("device_info_org_id")
    /**
     * Device信息ORGID
    */
    private String deviceInfoOrgId;

    /**
     * 设备名称
     */
    @TableField("device_info_name")
    /**
     * Device信息名称
    */
    private String deviceInfoName;

    /**
     * 厂商标识，例如 hikvision、dahua
     */
    @TableField("device_info_vendor")
    /**
     * Device信息供应商
    */
    private String deviceInfoVendor;

    /**
     * 设备型号
     */
    @TableField("device_info_model")
    /**
     * Device信息模型
    */
    private String deviceInfoModel;

    /**
     * 设备大类
     * <p>枚举值：camera（摄像头）、accessControl（门禁）、alarm（报警）、intercom（对讲）</p>
     */
    @TableField("device_info_category")
    /**
     * Device信息分类
    */
    private String deviceInfoCategory;

    /**
     * 设备小类（子类）
     * <p>由前端写死枚举值，例如 bullet、dome、ptz、cardReader、fingerprint、panicButton 等。</p>
     */
    @TableField("device_info_sub_category")
    /**
     * Device信息SUB分类
    */
    private String deviceInfoSubCategory;

    /**
     * IP 地址
     */
    @TableField("device_info_ip")
    /**
     * Device信息IP
    */
    private String deviceInfoIp;

    /**
     * 端口
     */
    @TableField("device_info_port")
    /**
     * Device信息端口
    */
    private Integer deviceInfoPort;

    /**
     * 用户名
     */
    @TableField("device_info_username")
    /**
     * Device信息用户名
    */
    private String deviceInfoUsername;

    /**
     * 密码（加密存储）
     * <p>只允许写入、不允许序列化：任何列表/详情响应都不会带出密文或明文。</p>
     */
    @TableField("device_info_password")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String deviceInfoPassword;

    /**
     * 是否在线
     */
    @TableField("device_info_online")
    /**
     * Device信息online
    */
    private Boolean deviceInfoOnline;

    /**
     * 设备序列号
     */
    @TableField("device_info_serial")
    /**
     * Device信息串行
    */
    private String deviceInfoSerial;

    /**
     * 固件版本
     */
    @TableField("device_info_firmware")
    /**
     * Device信息firmware
    */
    private String deviceInfoFirmware;

    /**
     * 通道数量
     */
    @TableField("device_info_channel_count")
    /**
     * Device信息通道数量
    */
    private Integer deviceInfoChannelCount;

    /**
     * 纬度（地图位置）
     */
    @TableField("device_info_latitude")
    /**
     * Device信息latitude
    */
    private BigDecimal deviceInfoLatitude;

    /**
     * 经度（地图位置）
     */
    @TableField("device_info_longitude")
    /**
     * Device信息longitude
    */
    private BigDecimal deviceInfoLongitude;

    /**
     * 详细地址
     */
    @TableField("device_info_address")
    /**
     * Device信息地址
    */
    private String deviceInfoAddress;

    /**
     * 离线检测阈值（秒），超过该秒数未收到心跳视为离线
     */
    @TableField("device_info_offline_threshold")
    /**
     * Device信息offline阈值
    */
    private Integer deviceInfoOfflineThreshold;

    /**
     * 设备编码（资产编码或厂商序列号，列表可复制）
     */
    @TableField("device_info_code")
    private String deviceInfoCode;

    /**
     * 厂商侧设备唯一标识（同步幂等键之一）
     */
    @TableField("device_info_external_id")
    private String deviceInfoExternalId;

    /**
     * 绑定的提供商凭据 ID；为空时回退该厂商默认套
     */
    @TableField("device_info_credential_id")
    private String deviceInfoCredentialId;

    /**
     * 负责人用户 ID
     */
    @TableField("device_info_owner_id")
    private String deviceInfoOwnerId;

    /**
     * 负责人姓名（冗余，列表直出）
     */
    @TableField("device_info_owner_name")
    private String deviceInfoOwnerName;

    /**
     * 负责人部门
     */
    @TableField("device_info_owner_dept")
    private String deviceInfoOwnerDept;

    /**
     * 负责人电话
     */
    @TableField("device_info_owner_phone")
    private String deviceInfoOwnerPhone;

    /**
     * 设备来源：local 手工录入、vendor 厂商同步
     */
    @TableField("device_info_source")
    private String deviceInfoSource;

    /**
     * 主码流地址（平台返回，可空）
     */
    @TableField("device_info_stream_main_url")
    private String deviceInfoStreamMainUrl;

    /**
     * 子码流地址
     */
    @TableField("device_info_stream_sub_url")
    private String deviceInfoStreamSubUrl;

    /**
     * 最后在线时间
     */
    @TableField("device_info_last_online_at")
    private LocalDateTime deviceInfoLastOnlineAt;

    /**
     * 最后离线时间
     */
    @TableField("device_info_last_offline_at")
    private LocalDateTime deviceInfoLastOfflineAt;

    /**
     * 安装日期
     */
    @TableField("device_info_install_at")
    private LocalDate deviceInfoInstallAt;

    /**
     * 维保到期
     */
    @TableField("device_info_warranty_end")
    private LocalDate deviceInfoWarrantyEnd;

    /**
     * 区域/楼层
     */
    @TableField("device_info_area")
    private String deviceInfoArea;

    /**
     * 标签，逗号分隔
     */
    @TableField("device_info_tags")
    private String deviceInfoTags;

    /**
     * 设备状态：normal/disabled/maintenance/scrapped
     */
    @TableField("device_info_status")
    private String deviceInfoStatus;

    /**
     * 备注
     */
    @TableField("device_info_remark")
    private String deviceInfoRemark;

    /**
     * 乐观锁/增量序号
     */
    @TableField("device_info_version")
    private Integer deviceInfoVersion;

    /**
     * 创建时间
     */
    @TableField("device_info_created_at")
    /**
     * Device信息创建时间AT
    */
    private LocalDateTime deviceInfoCreatedAt;

    /**
     * 更新时间
     */
    @TableField("device_info_updated_at")
    /**
     * Device信息更新时间AT
    */
    private LocalDateTime deviceInfoUpdatedAt;
}
