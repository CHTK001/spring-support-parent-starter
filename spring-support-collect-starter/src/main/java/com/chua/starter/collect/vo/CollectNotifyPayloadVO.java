package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Webhook 通知的请求体。
 *
 * @param subject     通知标题
 * @param content     通知正文
 * @param contentType 正文格式
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-通知回调出参")
public record CollectNotifyPayloadVO(
        @Schema(description = "通知标题")
        String subject,
        @Schema(description = "通知正文")
        String content,
        @Schema(description = "正文格式")
        String contentType) {
}
