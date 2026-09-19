package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 按内容发送请求
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@Schema(description = "按内容发送请求")
public class MessageContentSendRequest {

    /**
     * 提供商标识
     */
    @Schema(description = "提供商ID")
    /** SYS消息提供者标识 */
    private Integer sysMessageProviderId;

    /**
    * 接收人
    */
    @Schema(description = "接收人")
    /** 目标 */
    private String[] target;

    /**
    * 消息标题
    */
    @Schema(description = "消息标题")
    /** 主题 */
    private String subject;

    /**
    * 消息内容
    */
    @Schema(description = "消息内容")
    /** 内容 */
    private String content;

    /**
    * 消息参数
    */
    @Schema(description = "消息参数")
    private Map<String, Object> params; // 参数
}
