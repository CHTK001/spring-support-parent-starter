package com.chua.starter.collect.dto;

/**
 * 一次通知发送的入参。
 *
 * <p>字段按渠道各取所需：邮件用 {@code target}/{@code subject}/{@code content}，
 * 回调地址同样从 {@code target} 取。新增渠道不需要改动发送方。</p>
 *
 * @param target      接收目标：邮箱（逗号分隔）或 HTTP 回调地址
 * @param subject     标题
 * @param content     正文
 * @param contentType 正文格式 - text/html/json
 * @param timeoutMs   发送超时毫秒
 * @author CH
 * @since 4.0.0.42
 */
public record CollectNotifyMessage(
        String target,
        String subject,
        String content,
        String contentType,
        long timeoutMs) {
}
