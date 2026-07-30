package com.chua.spring.support.email.entity;

import lombok.Data;
import java.util.List;

/**
 * 邮箱账户导出数据结构
 * 
 * @author CH
 */
@Data
public class EmailAccountExport {

    /**
     * 导出版本
     */
    private String version = "1.0";

    /**
     * 导出时间
     */
    private Long exportTime;

    /**
     * 账户列表
     */
    private List<EmailAccountData> accounts;

    /**
     * 分组信息
     */
    private List<GroupData> groups;

    /**
     * 账户数据
     */
    @Data
    public static class EmailAccountData {
        private String emailAddress;
        private String displayName;
        private String smtpHost;
        private Integer smtpPort;
        private String imapHost;
        private Integer imapPort;
        private String username;
        private String password; // 加密后的密码
        private String protocol;
        private Boolean sslEnabled;
        private Boolean isDefault;
        private String groupId;
        private List<String> tags;
        private String proxyType;
        private String proxyHost;
        private Integer proxyPort;
    }

    /**
     * 分组数据
     */
    @Data
    public static class GroupData {
        private String id;
        private String name;
        private String description;
        private String color;
    }
}
