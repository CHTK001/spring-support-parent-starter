package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 按内容发送请求
 *
 * @param sysMessageProviderId 提供商ID
 * @param target               接收人
 * @param subject              消息标题
 * @param content              消息内容
 * @param params               消息参数
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "按内容发送请求")
public record MessageContentSendRequest(
        @Schema(description = "提供商ID") Integer sysMessageProviderId,
        @Schema(description = "接收人") String[] target,
        @Schema(description = "消息标题") String subject,
        @Schema(description = "消息内容") String content,
        @Schema(description = "消息参数") Map<String, Object> params) {
}
