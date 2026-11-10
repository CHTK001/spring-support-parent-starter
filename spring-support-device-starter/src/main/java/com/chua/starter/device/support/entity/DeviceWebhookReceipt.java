package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * webhook 入站幂等回执。
 *
 * <p>门禁记录表 / 告警表都不加外部 ID 列（不动既有表结构），统一由本表按
 * (source, externalId, topic) 记录已收过的包，重复推送直接跳过。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_webhook_receipt")
public class DeviceWebhookReceipt {

    /** 主键 */
    @TableId(value = "device_webhook_receipt_id", type = IdType.ASSIGN_UUID)
    private String deviceWebhookReceiptId;

    /** 来源（appKey 名称） */
    @TableField("device_webhook_receipt_source")
    private String deviceWebhookReceiptSource;

    /** 外部事件 ID；未传时取包体摘要 */
    @TableField("device_webhook_receipt_external_id")
    private String deviceWebhookReceiptExternalId;

    /** 主题：access / alarm */
    @TableField("device_webhook_receipt_topic")
    private String deviceWebhookReceiptTopic;

    /** 入库时间 */
    @TableField("device_webhook_receipt_created_at")
    private LocalDateTime deviceWebhookReceiptCreatedAt;
}
