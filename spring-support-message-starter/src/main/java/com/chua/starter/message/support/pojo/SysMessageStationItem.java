package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 当前用户站内消息项
 * <p>
 * 面向登录用户的「我的站内消息」查询结果，仅返回展示所需字段，
 * 避免将推送日志内部字段（提供商/模板/成本等）暴露给普通用户。
 * </p>
 *
 * @author CH
 * @since 2026/08/03
 */
@Data
public class SysMessageStationItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（推送日志ID，作为已读游标使用）
     */
    @Schema(description = "消息ID（已读游标）")
    private Integer id;

    /**
     * 消息主题
     */
    @Schema(description = "消息主题")
    private String subject;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    private String content;

    /**
     * 发送时间
     */
    @Schema(description = "发送时间")
    private LocalDateTime sendTime;

    /**
     * 已读状态：0-未读 / 1-已读
     */
    @Schema(description = "已读状态：0-未读 / 1-已读")
    private Integer readStatus;
}
