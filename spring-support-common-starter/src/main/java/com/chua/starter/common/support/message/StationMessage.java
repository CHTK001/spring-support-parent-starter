package com.chua.starter.common.support.message;

/**
 * 消息中心的站内消息投递内容。
 *
 * @param title 消息标题
 * @param content 消息正文
 * @param type 消息类型
 * @param level 消息级别
 * @param channel 消息渠道
 * @param senderId 发送人 ID
 * @param senderName 发送人名称
 */
public record StationMessage(
        String title,
        String content,
        String type,
        String level,
        String channel,
        Integer senderId,
        String senderName
) {
}
