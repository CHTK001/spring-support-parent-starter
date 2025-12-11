# spring-support-tencent-starter

## 📖 模块简介

**腾讯云服务集成模块** - 提供腾讯云各项服务的集成功能，包括微信小程序、云存储、短信等服务。

## ✨ 核心功能

### 📱 微信小程序

- ✅ 小程序登录
- ✅ 用户信息获取
- ✅ 消息推送
- ✅ 二维码生成

### 💬 短信服务

- ✅ 短信发送
- ✅ 模板短信
- ✅ 验证码短信
- ✅ 发送状态查询

### 📦 云存储

- ✅ 文件上传
- ✅ 文件下载
- ✅ 文件删除
- ✅ CDN 加速

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tencent-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  tencent:
    mini-app:
      # 是否启用微信小程序功能
      # 默认: false
      enable: true

      # 小程序 AppID
      app-id: your-app-id

      # 小程序 AppSecret
      app-secret: your-app-secret
```

### 3. 小程序登录

```java
@Service
public class WechatService {

    @Autowired
    private TencentMiniAppService miniAppService;

    public WechatSession login(String code) {
        return miniAppService.code2Session(code);
    }
}
```

## ⚙️ 配置说明

### 微信小程序配置

```yaml
plugin:
  tencent:
    mini-app:
      enable: true
      app-id: wx1234567890abcdef
      app-secret: your-app-secret

      # Token 配置
      token: your-token

      # 消息加密密钥
      aes-key: your-aes-key
```

### 短信服务配置

```yaml
plugin:
  tencent:
    sms:
      enable: true
      secret-id: your-secret-id
      secret-key: your-secret-key

      # 短信应用ID
      app-id: 1400000000

      # 短信签名
      sign-name: 您的签名

      # 模板ID
      template-id: 123456
```

## 💡 使用示例

### 小程序登录

```java
@RestController
@RequestMapping("/wechat")
public class WechatController {

    @Autowired
    private TencentMiniAppService miniAppService;

    @PostMapping("/login")
    public ReturnResult<WechatSession> login(@RequestBody WechatLoginRequest request) {
        WechatSession session = miniAppService.code2Session(request.getCode());
        return ReturnResult.success(session);
    }
}
```

### 发送短信

```java
@Service
public class SmsService {

    @Autowired
    private TencentSmsService smsService;

    public void sendVerifyCode(String phone, String code) {
        Map<String, String> params = new HashMap<>();
        params.put("code", code);

        smsService.sendTemplate(phone, "123456", params);
    }
}
```

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
