package com.chua.spring.support.email.entity;

import lombok.Data;
import java.util.List;

/**
 * 邮箱账户实体
 * 
 * @author CH
 */
@Data
public class EmailAccount {
    private String id;
    private String emailAddress;
    private String displayName;
    private String smtpHost;
    private Integer smtpPort;
    private String imapHost;
    private Integer imapPort;
    private String username;
    private String password;
    private String protocol;
    private Boolean sslEnabled;
    private Boolean isDefault;

    // 扩展字段
    private String groupId;
    private List<String> tags;
    private String proxyType;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String machineCode;
    private Long lastUsedTime;
    private Long tokenExpireTime;
    private Boolean autoRefresh;
    private Integer sortOrder;
    private String notes;
}
