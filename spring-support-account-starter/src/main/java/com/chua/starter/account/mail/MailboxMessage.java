package com.chua.starter.account.mail;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 信箱消息视图（收信 / 发信统一结构）。
 *
 * <p>{@code id} 由收信实现决定（IMAP 用 UID，POP3 用序号），只在同一
 * {@code folder} 内唯一，因此取详情时 {@code folder} 必须一起带上。</p>
 *
 * <p>{@code content} 只在取详情时返回；列表接口把它置空，只给 {@code preview}，
 * 避免一屏 20 封 HTML 邮件把响应体撑大。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
public class MailboxMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息标识（同一文件夹内唯一）
     */
    private String id;

    /**
     * 所在文件夹（服务端名）
     */
    private String folder;

    /**
     * 主题
     */
    private String subject;

    /**
     * 发件人
     */
    private String from;

    /**
     * 收件人
     */
    private String to;

    /**
     * 抄送
     */
    private String cc;

    /**
     * 发送时间（毫秒）
     */
    private Long sentAt;

    /**
     * 纯文本摘要（列表用）
     */
    private String preview;

    /**
     * 正文（详情用；列表为空）
     */
    private String content;

    /**
     * 正文类型（{@code text} / {@code html}）
     */
    private String contentType;

    /**
     * 是否未读
     */
    private Boolean unread;

    /**
     * 是否带附件
     */
    private Boolean hasAttachment;

    /**
     * 附件名列表
     */
    private List<String> attachments;
}
