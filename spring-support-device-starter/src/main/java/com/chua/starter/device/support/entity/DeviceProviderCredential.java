package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商接入凭据实体
 * <p>对应数据库表 device_provider_credential。</p>
 *
 * <p><b>为什么单独建表</b>：{@code DeviceConnectInfo} 携带 appKey/appSecret/platformType，
 * 但 {@link DeviceInfo} 无对应列，凭据在 API 边界被接收后即丢弃，云平台会话无法重建。
 * 本表补上该缺口。</p>
 *
 * <p><b>粒度</b>：同一 (厂商, 平台类型) 可登记多套凭据（如多个海康账号分属不同区域），
 * 用 {@code deviceCredentialName} 区分；每厂商可标记一套为默认，厂商级离线阈值取默认套的阈值。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_provider_credential")
public class DeviceProviderCredential {

    /** 主键 标识 */
    @TableId(value = "device_credential_id", type = IdType.ASSIGN_UUID)
    private String deviceCredentialId;

    /** 厂商 标识：hikvision / dahua */
    @TableField("device_credential_vendor")
    private String deviceCredentialVendor;

    /** 平台 类型：cloud-yao / cloud-eye / secure-platform / dahua-cloud / lechange；SDK 直连为空 */
    @TableField("device_credential_platform_type")
    private String deviceCredentialPlatformType;

    /** 凭据 名称，如「华东区账号」；同厂商同平台下唯一 */
    @TableField("device_credential_name")
    private String deviceCredentialName;

    /** AppKey（AK）；SDK 直连模式可空 */
    @TableField("device_credential_app_key")
    private String deviceCredentialAppKey;

    /** AppSecret（SK）；SDK 直连模式可空 */
    @TableField("device_credential_app_secret")
    private String deviceCredentialAppSecret;

    /** 平台 endpoint 覆盖（留空用各 Session 内置默认） */
    @TableField("device_credential_base_url")
    private String deviceCredentialBaseUrl;

    /** 离线 检测阈值（秒） */
    @TableField("device_credential_offline_threshold")
    private Integer deviceCredentialOfflineThreshold;

    /** 是否 为该厂商默认套：0 否、1 是 */
    @TableField("device_credential_is_default")
    private Boolean deviceCredentialIsDefault;

    /** 是否 启用：0 否、1 是 */
    @TableField("device_credential_enabled")
    private Boolean deviceCredentialEnabled;

    /** 备注 */
    @TableField("device_credential_remark")
    private String deviceCredentialRemark;

    /** 创建 时间 */
    @TableField("device_credential_created_at")
    private LocalDateTime deviceCredentialCreatedAt;

    /** 更新 时间 */
    @TableField("device_credential_updated_at")
    private LocalDateTime deviceCredentialUpdatedAt;
}