package com.chua.starter.device.support.model;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备新增/编辑请求
 *
 * <p>把“连接参数”（厂商、平台、AK/SK、IP 端口、序列号）与“资产字段”
 * （名称、编码、组织、负责人、坐标、维保）放在同一个请求里，避免
 * {@code DeviceConnectInfo} 承担台账职责，也避免前端字段在 API 边界被丢弃。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class DeviceSaveRequest {

    // ==================== 资产字段 ====================

    /** 设备名称 */
    private String deviceInfoName;

    /** 设备编码（资产编码/序列号，列表可复制） */
    private String deviceInfoCode;

    /** 所属组织 ID */
    private String deviceInfoOrgId;

    /** 厂商标识 */
    private String deviceInfoVendor;

    /** 设备型号 */
    private String deviceInfoModel;

    /** 大类：camera / accessControl / alarm / intercom */
    private String deviceInfoCategory;

    /** 小类：bullet / dome / ptz / cardReader ... */
    private String deviceInfoSubCategory;

    /** 设备 IP（SDK 直连） */
    private String deviceInfoIp;

    /** 设备端口（SDK 直连） */
    private Integer deviceInfoPort;

    /** 设备用户名（SDK 直连） */
    private String deviceInfoUsername;

    /** 设备密码（SDK 直连）；编辑时留空表示不修改 */
    private String deviceInfoPassword;

    /** 设备序列号（资产） */
    private String deviceInfoSerial;

    /** 固件版本 */
    private String deviceInfoFirmware;

    /** 通道数 */
    private Integer deviceInfoChannelCount;

    /** 纬度（WGS84） */
    private BigDecimal deviceInfoLatitude;

    /** 经度（WGS84） */
    private BigDecimal deviceInfoLongitude;

    /** 详细地址 */
    private String deviceInfoAddress;

    /** 离线检测阈值（秒） */
    private Integer deviceInfoOfflineThreshold;

    /** 绑定的提供商凭据 ID；为空回退厂商默认套 */
    private String deviceInfoCredentialId;

    /** 负责人用户 ID */
    private String deviceInfoOwnerId;

    /** 负责人姓名 */
    private String deviceInfoOwnerName;

    /** 负责人部门 */
    private String deviceInfoOwnerDept;

    /** 负责人电话 */
    private String deviceInfoOwnerPhone;

    /** 来源：local / vendor */
    private String deviceInfoSource;

    /** 主码流地址 */
    private String deviceInfoStreamMainUrl;

    /** 子码流地址 */
    private String deviceInfoStreamSubUrl;

    /** 安装日期 */
    private LocalDate deviceInfoInstallAt;

    /** 维保到期 */
    private LocalDate deviceInfoWarrantyEnd;

    /** 区域/楼层 */
    private String deviceInfoArea;

    /** 标签，逗号分隔 */
    private String deviceInfoTags;

    /** 状态：normal / disabled / maintenance / scrapped */
    private String deviceInfoStatus;

    /** 备注 */
    private String deviceInfoRemark;

    // ==================== 连接字段 ====================

    /** 接入方式：PLATFORM / SDK */
    private String authType;

    /** 平台类型：cloud-yao / cloud-eye / secure-platform / dahua-cloud / lechange */
    private String platformType;

    /** 平台 AppKey（AK） */
    private String appKey;

    /** 平台 AppSecret（SK） */
    private String appSecret;

    /** 平台设备序列号 */
    private String deviceSerial;

    /** 新增时是否立即建连；默认 true */
    private Boolean connect;

    /**
     * 构造连接信息
     *
     * @return 厂商 SPI 需要的连接信息
     */
    public DeviceConnectInfo toConnectInfo() {
        return new DeviceConnectInfo(
                deviceInfoVendor,
                authType,
                deviceInfoIp,
                deviceInfoPort,
                deviceInfoUsername,
                deviceInfoPassword,
                platformType,
                appKey,
                appSecret,
                deviceSerial,
                null);
    }

    /**
     * 构造设备实体（只含资产字段，密码由服务层加密后写入）
     *
     * @return 设备实体
     */
    public DeviceInfo toEntity() {
        DeviceInfo device = new DeviceInfo();
        device.setDeviceInfoName(deviceInfoName);
        device.setDeviceInfoCode(deviceInfoCode);
        device.setDeviceInfoOrgId(deviceInfoOrgId);
        device.setDeviceInfoVendor(deviceInfoVendor);
        device.setDeviceInfoModel(deviceInfoModel);
        device.setDeviceInfoCategory(deviceInfoCategory);
        device.setDeviceInfoSubCategory(deviceInfoSubCategory);
        device.setDeviceInfoIp(deviceInfoIp);
        device.setDeviceInfoPort(deviceInfoPort);
        device.setDeviceInfoUsername(deviceInfoUsername);
        device.setDeviceInfoSerial(deviceInfoSerial);
        device.setDeviceInfoFirmware(deviceInfoFirmware);
        device.setDeviceInfoChannelCount(deviceInfoChannelCount);
        device.setDeviceInfoLatitude(deviceInfoLatitude);
        device.setDeviceInfoLongitude(deviceInfoLongitude);
        device.setDeviceInfoAddress(deviceInfoAddress);
        device.setDeviceInfoOfflineThreshold(deviceInfoOfflineThreshold);
        device.setDeviceInfoCredentialId(deviceInfoCredentialId);
        device.setDeviceInfoOwnerId(deviceInfoOwnerId);
        device.setDeviceInfoOwnerName(deviceInfoOwnerName);
        device.setDeviceInfoOwnerDept(deviceInfoOwnerDept);
        device.setDeviceInfoOwnerPhone(deviceInfoOwnerPhone);
        device.setDeviceInfoSource(deviceInfoSource);
        device.setDeviceInfoStreamMainUrl(deviceInfoStreamMainUrl);
        device.setDeviceInfoStreamSubUrl(deviceInfoStreamSubUrl);
        device.setDeviceInfoInstallAt(deviceInfoInstallAt);
        device.setDeviceInfoWarrantyEnd(deviceInfoWarrantyEnd);
        device.setDeviceInfoArea(deviceInfoArea);
        device.setDeviceInfoTags(deviceInfoTags);
        device.setDeviceInfoStatus(deviceInfoStatus);
        device.setDeviceInfoRemark(deviceInfoRemark);
        return device;
    }
}
