package com.chua.starter.account.mail;

import lombok.Data;

import java.io.Serializable;

/**
 * 信箱取信条件。
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
public class MailboxQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 服务端文件夹名（默认收件箱）
     */
    private String folder = "INBOX";

    /**
     * 本次取多少封（默认 20，实现侧会做上限保护）
     */
    private Integer limit = 20;

    /**
     * 从最新一封往前跳过多少封（分页用）
     */
    private Integer offset = 0;

    /**
     * 只看未读
     */
    private Boolean unreadOnly = Boolean.FALSE;

    /**
     * 未读优先：先把未读邮件按时间倒序排在前面，不足一页再用最新的已读邮件补足。
     *
     * <p>只在首屏（{@code offset == 0}）生效：翻页时顺序必须稳定，否则
     * 「加载更多」会因为前面插入未读而重复或漏信。</p>
     */
    private Boolean preferUnread = Boolean.FALSE;

    /**
     * 主题 / 发件人模糊词
     */
    private String keyword;
}
