package com.chua.starter.server.support.model.vo;

import java.util.Date;

/**
 * 邮件条目
 * <p>
 * IMAP 拉取的单封邮件出参，列表接口给出截断正文、详情接口给出完整正文，
 * 字段名与历史 Map 载荷完全一致。
 * </p>
 *
 * @param subject 邮件主题
 * @param from    发件人
 * @param date    发送时间，未提供时为 {@code null}
 * @param size    邮件字节数
 * @param content 正文；列表接口按 400 字符截断，详情接口为完整正文
 * @author CH
 * @since 4.0.0.42
 */
public record MailMessageVO(
        String subject,
        String from,
        Date date,
        Integer size,
        String content
) {
}
