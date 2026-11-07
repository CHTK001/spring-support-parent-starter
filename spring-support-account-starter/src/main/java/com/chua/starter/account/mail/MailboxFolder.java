package com.chua.starter.account.mail;

import lombok.Data;

import java.io.Serializable;

/**
 * 信箱文件夹视图。
 *
 * <p>{@code key} 是邮件服务端真实文件夹名（IMAP 里可能是 {@code INBOX}、
 * {@code Sent Messages}、{@code [Gmail]/Sent Mail} 等），取信 / 取详情时必须原样回传；
 * {@code name} 只是给界面用的中文名。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
public class MailboxFolder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 服务端文件夹名（取信 / 取详情时原样回传）
     */
    private String key;

    /**
     * 界面展示名
     */
    private String name;

    /**
     * 文件夹语义（INBOX / SENT / DRAFT / TRASH / JUNK / OTHER）
     */
    private String role;

    /**
     * 邮件总数
     */
    private Integer total;

    /**
     * 未读数量
     */
    private Integer unread;
}
