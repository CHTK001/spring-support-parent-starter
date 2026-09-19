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
 * @since 2026/08/03
 * @author CH
 */
@Data
public class SysMessageStationItem implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 消息标识（推送日志标识，作为已读游标使用）
    */
    @Schema(description = "消息ID（已读游标）")
    /** 标识 */
    private Integer id;

    /**
    * 消息主题
    */
    @Schema(description = "消息主题")
    /** 主题 */
    private String subject;

    /**
    * 消息内容
    */
    @Schema(description = "消息内容")
    /** 内容 */
    private String content;

    /**
    * 发送时间
    */
    @Schema(description = "发送时间")
    /** 发送时间 */
    private LocalDateTime sendTime;

    /**
    * 已读状态：0-未读 / 1-已读
    */
    @Schema(description = "已读状态：0-未读 / 1-已读")
    /** 读取状态 */
    private Integer readStatus;
}
