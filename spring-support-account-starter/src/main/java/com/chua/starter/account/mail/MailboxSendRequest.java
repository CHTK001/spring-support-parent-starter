package com.chua.starter.account.mail;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 发信箱提交体。
 *
 * <p>发送统一走 utils 的 {@code MessagePush} SPI：邮件用 {@code email}，
 * 短信按账号 {@code vendor} 选 {@code alibaba-sms} / {@code tencent-sms} / {@code baidu-sms}。
 * 模板发送用 {@code templateCode} + {@code templateParams}，由实现侧替换占位符。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
public class MailboxSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 收件人（邮件地址 / 手机号）
     */
    private String to;

    /**
     * 抄送（仅邮件）
     */
    private List<String> cc;

    /**
     * 主题（仅邮件）
     */
    private String subject;

    /**
     * 正文
     */
    private String content;

    /**
     * 正文类型（{@code text} / {@code html}）
     */
    private String contentType;

    /**
     * 决策模板编码（短信 / 邮件模板）
     */
    private String templateCode;

    /**
     * 模板参数
     */
    private Map<String, String> templateParams;
}
