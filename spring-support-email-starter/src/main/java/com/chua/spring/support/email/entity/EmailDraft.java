package com.chua.spring.support.email.entity;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 邮件草稿实体
 * 
 * @author CH
 */
@Data
public class EmailDraft {

    /**
     * 草稿ID
     */
    private String id;

    /**
     * 账户ID
     */
    private String accountId;

    /**
     * 收件人列表
     */
    private List<String> toAddresses;

    /**
     * 抄送列表
     */
    private List<String> ccAddresses;

    /**
     * 密送列表
     */
    private List<String> bccAddresses;

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * 邮件内容（纯文本）
     */
    private String contentText;

    /**
     * 邮件内容（HTML）
     */
    private String contentHtml;

    /**
     * 附件ID列表
     */
    private List<String> attachmentIds;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
