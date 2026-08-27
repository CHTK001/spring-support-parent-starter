package com.chua.starter.ai.support.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bot 绑定列表响应。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class BotBindingListResponse {

    /**
     * 绑定 ID
     */
    private Long id;

    /**
     * Bot 唯一标识
     */
    private String botId;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * Bot 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 独立网关端口号
     */
    private Integer gatewayPort;

    /**
     * 状态：online / offline / expired
     */
    private String status;

    /**
     * 绑定来源
     */
    private String bindSource;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;
}
