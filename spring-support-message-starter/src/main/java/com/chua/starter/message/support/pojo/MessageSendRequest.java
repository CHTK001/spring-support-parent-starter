package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 消息发送请求
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@Schema(description = "消息发送请求")
public class MessageSendRequest {

    /**
     * 接收人
     */
    @Schema(description = "接收人")
    /** 目标 */
    private String[] target;

    /**
     * 模板标识
     */
    @Schema(description = "模板ID")
    /** SYS消息模板标识 */
    private Integer sysMessageTemplateId;

    /**
     * 消息标题
     */
    @Schema(description = "消息标题")
    /** 主题 */
    private String subject;

    /**
     * 消息参数
     */
    @Schema(description = "消息参数")
    private java.util.Map<String, Object> params; // 参数
}
