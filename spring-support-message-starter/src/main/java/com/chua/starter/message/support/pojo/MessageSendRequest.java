package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 消息发送请求
 *
 * @param target                接收人
 * @param sysMessageTemplateId  模板ID
 * @param subject               消息标题
 * @param params                消息参数
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "消息发送请求")
public record MessageSendRequest(
        @Schema(description = "接收人") String[] target,
        @Schema(description = "模板ID") Integer sysMessageTemplateId,
        @Schema(description = "消息标题") String subject,
        @Schema(description = "消息参数") Map<String, Object> params) {
}
