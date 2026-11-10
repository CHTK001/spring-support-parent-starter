package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入站 webhook 应用凭据。
 *
 * <p>外部系统推送门禁记录 / 告警事件时凭 appKey + secret 做 HMAC 签名；
 * expiresAt 为空表示永久有效，enabled=0 表示停用。secret 只在创建时返回一次。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_webhook_appkey")
public class DeviceWebhookAppKey {

    /** 主键 */
    @TableId(value = "device_webhook_appkey_id", type = IdType.ASSIGN_UUID)
    private String deviceWebhookAppkeyId;

    /** 公开标识（appKey） */
    @TableField("device_webhook_appkey_key")
    private String deviceWebhookAppkeyKey;

    /** 签名密钥（仅创建时明文返回） */
    @TableField("device_webhook_appkey_secret")
    private String deviceWebhookAppkeySecret;

    /** 凭据名称，同时作为入站数据的 source */
    @TableField("device_webhook_appkey_name")
    private String deviceWebhookAppkeyName;

    /** 允许推送的主题，逗号分隔：access,alarm */
    @TableField("device_webhook_appkey_scopes")
    private String deviceWebhookAppkeyScopes;

    /** 过期时间；为空表示永久有效 */
    @TableField("device_webhook_appkey_expires_at")
    private LocalDateTime deviceWebhookAppkeyExpiresAt;

    /** 是否启用：1 启用、0 停用 */
    @TableField("device_webhook_appkey_enabled")
    private Integer deviceWebhookAppkeyEnabled;

    /** 最近一次成功调用时间 */
    @TableField("device_webhook_appkey_last_used_at")
    private LocalDateTime deviceWebhookAppkeyLastUsedAt;

    /** 创建时间 */
    @TableField("device_webhook_appkey_created_at")
    private LocalDateTime deviceWebhookAppkeyCreatedAt;
}
