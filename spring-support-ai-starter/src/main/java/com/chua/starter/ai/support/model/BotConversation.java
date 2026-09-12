package com.chua.starter.ai.support.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
* 机器人 会话列表项（IM 左侧会话列表）。
*
* <p>一个会话 = 一个 Bot 与一个微信用户之间的全部往来消息，
* 聚合展示最后一条消息与时间。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
public class BotConversation {

    /**
    * 机器人 唯一标识
     */
    private String botId;

    /**
    * 微信用户 标识
     */
    private String fromUser;

    /**
    * 展示昵称（绑定记录 nickname，缺失回退 从用户）
     */
    private String nickname;

    /**
    * 平台类型（wechat / wecom / feishu，来自绑定记录）
     */
    private String platform;

    /**
    * 最后一条消息内容
     */
    private String lastContent;

    /**
    * 最后一条消息时间
     */
    private LocalDateTime lastTime;

    /**
    * 未读消息数（入 方向且未读；当前简化实现恒为 0，供后续扩展）
     */
    private long unread;

    /**
    * 绑定记录状态（online / offline / expired）
     */
    private String status;
}