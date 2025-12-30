# Spring Support Message Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Message Starter 是一个统一的消息通知模块，支持邮件、短信、微信公众号等多种通知渠道，提供统一的消息发送接口。

### ✨ 主要特性

- 📧 **邮件发送** - 支持文本、HTML、附件邮件
- 📱 **短信发送** - 支持阿里云、腾讯云短信服务
- 💬 **微信通知** - 支持微信公众号模板消息
- 📋 **模板管理** - 支持消息模板配置
- 🔄 **异步发送** - 支持异步消息发送
- 📊 **发送追踪** - 消息发送状态追踪

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-message-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

### 邮件配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.message.email.enabled` | Boolean | false | 是否启用邮件 |
| `spring.mail.host` | String | - | SMTP服务器地址 |
| `spring.mail.port` | Integer | 587 | SMTP端口 |
| `spring.mail.username` | String | - | 邮箱用户名 |
| `spring.mail.password` | String | - | 邮箱密码 |

### 短信配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.message.sms.enabled` | Boolean | false | 是否启用短信 |
| `plugin.message.sms.provider` | String | aliyun | 短信服务商：aliyun, tencent |
| `plugin.message.sms.access-key` | String | - | AccessKey |
| `plugin.message.sms.secret-key` | String | - | SecretKey |
| `plugin.message.sms.sign-name` | String | - | 短信签名 |

### 微信配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.message.wechat.enabled` | Boolean | false | 是否启用微信通知 |
| `plugin.message.wechat.app-id` | String | - | 公众号AppID |
| `plugin.message.wechat.app-secret` | String | - | 公众号AppSecret |

### 配置示例

```yaml
plugin:
  message:
    enable: true
    
    # 邮件配置
    email:
      enabled: true

spring:
  mail:
    host: smtp.qq.com
    port: 587
    username: your-email@qq.com
    password: your-password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

plugin:
  message:
    # 短信配置
    sms:
      enabled: true
      provider: aliyun
      access-key: your-access-key
      secret-key: your-secret-key
      sign-name: 您的签名
    
    # 微信配置
    wechat:
      enabled: true
      app-id: your-app-id
      app-secret: your-app-secret
```

## 📝 使用示例

### 发送邮件

```java
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;
    
    public void sendSimpleEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("your-email@qq.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }
    
    public void sendHtmlEmail(String to, String subject, String htmlContent) 
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom("your-email@qq.com");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }
}
```

### 发送短信

```java
@Service
public class SmsService {

    @Autowired
    private SmsTemplate smsTemplate;
    
    public void sendVerificationCode(String phone, String code) {
        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        
        smsTemplate.send(phone, "SMS_TEMPLATE_ID", params);
    }
}
```

### 发送微信模板消息

```java
@Service
public class WeChatService {

    @Autowired
    private WeChatTemplate weChatTemplate;
    
    public void sendTemplateMessage(String openId, String templateId, 
                                    Map<String, String> data) {
        weChatTemplate.send(openId, templateId, data);
    }
}
```

## 🔗 相关链接

- [返回主文档](../README.md)
- [Spring Boot Mail文档](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.email)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
