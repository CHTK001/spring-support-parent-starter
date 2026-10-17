package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 当前用户站内消息项
 * <p>
 * 面向登录用户的「我的站内消息」查询结果，仅返回展示所需字段，
 * 避免将推送日志内部字段（提供商/模板/成本等）暴露给普通用户。
 * </p>
 *
 * @param id         消息ID（已读游标）
 * @param subject    消息主题
 * @param content    消息内容
 * @param sendTime   发送时间
 * @param readStatus 已读状态：0-未读 / 1-已读
 * @since 2026/08/03
 * @author CH
 */
public record SysMessageStationItem(
        @Schema(description = "消息ID（已读游标）") Integer id,
        @Schema(description = "消息主题") String subject,
        @Schema(description = "消息内容") String content,
        @Schema(description = "发送时间") LocalDateTime sendTime,
        @Schema(description = "已读状态：0-未读 / 1-已读") Integer readStatus) implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;
}
