package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String deviceCredentialAppSecret;

    /** 云曜产品编码（ProductCode），云曜平台必填 */
    @TableField("device_credential_product_code")
    private String deviceCredentialProductCode;

    /** 云曜项目 ID（ProjectID），云曜平台必填 */
    @TableField("device_credential_project_id")
    private String deviceCredentialProjectId;

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

    /**
     * 在离线检测开关：该凭据维度的设备是否由时间轮自动探测（能力位 DEVICE）
     */
    @TableField("device_credential_offline_detect_enabled")
    private Boolean deviceCredentialOfflineDetectEnabled;

    /**
     * 在离线检测间隔（秒）
     */
    @TableField("device_credential_offline_detect_interval")
    private Integer deviceCredentialOfflineDetectInterval;

    /**
     * 事件轮询开关：该凭据实例是否独立拉取厂商事件（能力位 EVENT）
     */
    @TableField("device_credential_event_poll_enabled")
    private Boolean deviceCredentialEventPollEnabled;

    /**
     * 事件轮询间隔（秒）
     */
    @TableField("device_credential_event_poll_interval")
    private Integer deviceCredentialEventPollInterval;

    /**
     * 设备自动同步开关：该凭据维度是否由时间轮自动拉取设备目录
     */
    @TableField("device_credential_device_sync_enabled")
    private Boolean deviceCredentialDeviceSyncEnabled;

    /**
     * 设备自动同步间隔（秒）
     */
    @TableField("device_credential_device_sync_interval")
    private Integer deviceCredentialDeviceSyncInterval;

    /**
     * 组织自动同步开关：该凭据维度是否由时间轮自动拉取厂商侧组织树
     */
    @TableField("device_credential_org_sync_enabled")
    private Boolean deviceCredentialOrgSyncEnabled;

    /**
     * 组织自动同步间隔（秒）
     */
    @TableField("device_credential_org_sync_interval")
    private Integer deviceCredentialOrgSyncInterval;

    /** 创建 时间 */
    @TableField("device_credential_created_at")
    private LocalDateTime deviceCredentialCreatedAt;

    /** 更新 时间 */
    @TableField("device_credential_updated_at")
    private LocalDateTime deviceCredentialUpdatedAt;

    /**
     * 脱敏后的 AppSecret，仅用于响应展示。
     * <p>真实的 {@code deviceCredentialAppSecret} 标记为 WRITE_ONLY，永远不会出现在 JSON 中；
     * 前端需要判断“是否已配置”时读这个字段。</p>
     *
     * @return 形如 {@code ab****cd} 的掩码；未配置时返回空串
     */
    public String getDeviceCredentialAppSecretMasked() {
        String secret = deviceCredentialAppSecret;
        if (secret == null || secret.isBlank()) {
            return "";
        }
        // 密文不暴露任何字符，统一显示为 6 个星号
        if (secret.startsWith("enc:")) {
            return "******";
        }
        if (secret.length() <= 6) {
            return "******";
        }
        return secret.substring(0, 2) + "****" + secret.substring(secret.length() - 4);
    }

    /**
     * 是否已配置 AppSecret
     * <p>前端只需要知道“有没有配”，不需要拿到值。</p>
     *
     * @return true 表示已配置
     */
    public boolean isDeviceCredentialAppSecretConfigured() {
        return deviceCredentialAppSecret != null && !deviceCredentialAppSecret.isBlank();
    }
}