package com.chua.spring.support.email.entity;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 邮件消息实体
 * 
 * @author CH
 */
@Data
public class EmailMessage {
    private String id;
    private String accountId;
    private String messageId;
    private String folderName;
    private String subject;
    private String fromAddress;
    private List<String> toAddresses;
    private List<String> ccAddresses;
    private String contentText;
    private String contentHtml;
    private Boolean hasAttachments;
    private Boolean isRead;
    private Boolean isStarred;
    private Date sentDate;
    private Date receivedDate;
}
