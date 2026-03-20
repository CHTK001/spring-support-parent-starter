# Spring Support Email Starter

Spring Boot 邮箱收发管理模块，提供完整的邮箱收发 REST API 接口。

## 功能特性

- 📧 邮件收发接口
- 📥 邮件列表查询
- 📁 文件夹管理
- 👤 邮箱账户管理
- 🔍 邮件搜索
- 📎 附件处理
- 🔔 WebSocket 实时推送（待实现）

## 技术栈

- Spring Boot 3.x
- Spring Web
- Spring WebSocket
- utils-support-email-starter (邮件核心)
- Jakarta Mail API

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-email-starter</artifactId>
    <version>4.0.0.37</version>
</dependency>
```

### 自动配置

模块会自动配置所有必要的 Bean，无需额外配置。

## API 接口

### 邮件管理

#### 发送邮件

```http
POST /api/email/send
Content-Type: application/json

{
  "accountId": "账户ID",
  "toAddresses": ["recipient@example.com"],
  "ccAddresses": ["cc@example.com"],
  "subject": "邮件主题",
  "contentText": "邮件内容"
}
```

#### 获取邮件列表

```http
GET /api/email/list?accountId=xxx&folder=INBOX&page=1&size=20
```

#### 获取邮件详情

```http
GET /api/email/{id}
```

#### 删除邮件

```http
DELETE /api/email/{id}
```

#### 搜索邮件

```http
GET /api/email/search?keyword=关键词
```

### 账户管理

#### 获取账户列表

```http
GET /api/account/list
```

#### 添加账户

```http
POST /api/account/add
Content-Type: application/json

{
  "emailAddress": "user@example.com",
  "displayName": "我的邮箱",
  "smtpHost": "smtp.example.com",
  "smtpPort": 465,
  "imapHost": "imap.example.com",
  "imapPort": 993,
  "username": "user@example.com",
  "password": "password",
  "protocol": "imap",
  "sslEnabled": true,
  "isDefault": false
}
```

#### 更新账户

```http
PUT /api/account/{id}
Content-Type: application/json

{账户信息}
```

#### 删除账户

```http
DELETE /api/account/{id}
```

#### 测试连接

```http
POST /api/account/test
Content-Type: application/json

{账户信息}
```

## 核心类

### EmailService

邮件服务类，提供邮件收发核心功能。

```java
@Service
public class EmailService {
    // 发送邮件
    public void sendEmail(EmailAccount account, EmailMessage message);

    // 接收邮件
    public List<EmailMessage> receiveEmails(EmailAccount account, String folderName);

    // 测试连接
    public boolean testConnection(EmailAccount account);
}
```

### EmailController

邮件管理控制器，提供邮件相关 REST API。

### AccountController

账户管理控制器，提供账户管理 REST API。

## 实体类

### EmailAccount

邮箱账户实体。

```java
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
}
```

### EmailMessage

邮件消息实体。

```java
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
```

## 依赖模块

- `utils-support-email-starter` - 邮件核心功能
- `spring-support-common-starter` - Spring 通用工具

## 扩展功能

### 添加数据库持久化

可以集成 MyBatis 或 JPA 实现邮件和账户的数据库存储。

### 添加 WebSocket 推送

可以使用 Spring WebSocket 实现新邮件实时推送。

### 添加邮件模板

可以集成模板引擎（如 Thymeleaf）实现邮件模板功能。

## 注意事项

- 邮箱密码建议加密存储
- 建议使用 HTTPS 传输敏感信息
- 注意邮件服务器的连接限制
- 大附件建议使用分片上传
