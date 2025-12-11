# spring-support-email-starter

## 📖 模块简介

**邮件发送模块** - 提供邮件发送功能，支持文本邮件、HTML 邮件、附件邮件、模板邮件等多种邮件类型。

## ✨ 核心功能

### 📧 邮件发送

- ✅ 文本邮件
- ✅ HTML 邮件
- ✅ 附件邮件
- ✅ 模板邮件
- ✅ 批量发送

### 🎨 邮件模板

- ✅ Freemarker 模板
- ✅ Thymeleaf 模板
- ✅ 变量替换
- ✅ 模板缓存

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-email-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

**配置文件**：`application.yml`

```yaml
plugin:
  email:
    # 是否启用邮件服务
    # 默认: false
    # 说明: 设置为true时才会启用邮件发送功能
    enable: true

    # SMTP 服务器配置
    host: smtp.qq.com
    port: 587
    username: your-email@qq.com
    password: your-smtp-password

    # 发件人信息
    from: your-email@qq.com
    from-name: 系统通知

    # 编码
    default-encoding: UTF-8
```

### 3. 发送邮件

```java
@Autowired
private EmailService emailService;

// 发送文本邮件
emailService.sendText("to@example.com", "主题", "邮件内容");

// 发送HTML邮件
emailService.sendHtml("to@example.com", "主题", "<h1>HTML内容</h1>");

// 发送附件邮件
emailService.sendWithAttachment(
    "to@example.com",
    "主题",
    "内容",
    new File("attachment.pdf")
);
```

## ⚙️ 配置说明

### 完整配置示例

```yaml
plugin:
  email:
    # 功能开关
    enable: true

    # SMTP 服务器配置
    host: smtp.qq.com
    port: 587
    username: your-email@qq.com
    password: your-smtp-password

    # 发件人配置
    from: your-email@qq.com
    from-name: 系统通知

    # SSL/TLS 配置
    ssl:
      enable: true

    # 编码配置
    default-encoding: UTF-8

    # 超时配置（毫秒）
    timeout: 5000

    # 连接池配置
    pool:
      max-total: 10
      max-idle: 5
```

### 常用邮箱配置

**QQ 邮箱**：

```yaml
plugin:
  email:
    host: smtp.qq.com
    port: 587
    username: your-qq-email@qq.com
    password: your-authorization-code # 授权码，非QQ密码
```

**163 邮箱**：

```yaml
plugin:
  email:
    host: smtp.163.com
    port: 465
    username: your-email@163.com
    password: your-authorization-code
    ssl:
      enable: true
```

**Gmail**：

```yaml
plugin:
  email:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
```

## 💡 使用示例

### 发送文本邮件

```java
@Service
public class NotificationService {

    @Autowired
    private EmailService emailService;

    public void sendWelcomeEmail(String email, String username) {
        String subject = "欢迎注册";
        String content = String.format("您好 %s，欢迎注册我们的系统！", username);

        emailService.sendText(email, subject, content);
    }
}
```

### 发送 HTML 邮件

```java
public void sendHtmlEmail(String email) {
    String subject = "账户激活";
    String html = """
        <html>
        <body>
            <h1>账户激活</h1>
            <p>请点击以下链接激活您的账户：</p>
            <a href="https://example.com/activate?token=xxx">激活账户</a>
        </body>
        </html>
        """;

    emailService.sendHtml(email, subject, html);
}
```

### 发送模板邮件

```java
public void sendTemplateEmail(String email, Map<String, Object> data) {
    String subject = "订单通知";
    String template = "order-notification";  // 模板名称

    emailService.sendTemplate(email, subject, template, data);
}
```

### 批量发送

```java
public void sendBatchEmail(List<String> emails) {
    String subject = "系统通知";
    String content = "系统将于今晚22:00进行维护";

    emailService.sendBatch(emails, subject, content);
}
```

## 🎯 设计原则

### 1. 简单易用

- ✅ 简洁的 API 设计
- ✅ 合理的默认配置
- ✅ 详细的错误提示

### 2. 高性能

- ✅ 连接池管理
- ✅ 异步发送支持
- ✅ 批量发送优化

### 3. 可靠性

- ✅ 发送失败重试
- ✅ 发送日志记录
- ✅ 异常处理机制

## 🔗 相关模块

- [spring-support-common-starter](../spring-support-common-starter) - 公共基础模块

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
