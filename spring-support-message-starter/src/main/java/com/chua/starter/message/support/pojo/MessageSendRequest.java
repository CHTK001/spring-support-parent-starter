package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 消息发送请求
 *
 * @author CH
 * @since 2026/08/03
 */
@Data
@Schema(description = "消息发送请求")
public class MessageSendRequest {

    /**
     * 接收人
     */
    @Schema(description = "接收人")
    private String[] target;

    /**
     * 模板ID
     */
    @Schema(description = "模板ID")
    private Integer sysMessageTemplateId;

    /**
     * 消息标题
     */
    @Schema(description = "消息标题")
    private String subject;

    /**
     * 消息参数
     */
    @Schema(description = "消息参数")
    private java.util.Map<String, Object> params;
}
