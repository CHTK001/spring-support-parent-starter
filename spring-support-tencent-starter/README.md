# spring-support-tencent-starter

微信支付集成 Starter：把 JSAPI / H5 / Native / APP / 小程序五种下单方式，以及查单、关单、
退款、退款查询、支付通知与退款通知解析、支付分（先享后付）收敛成一个
`TencentWechatPayGateway` SPI，并把微信小程序登录注册成 oauth-client 的
`UserInfoService` 实现（`@Spi("WECHAT_MINI")`）。

- 包名：`com.chua.starter.tencent.support`
- 版本：`4.0.0.51`
- 范围：**只有微信支付 + 微信小程序登录**。本模块**不含**短信（SMS）、对象存储（COS）、
  公众号消息推送、小程序码生成等能力；早期 README 中这些描述不成立，已删除。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动装配 | `TencentConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 支付配置 | `TencentWechatPayProperties`（前缀 `plugin.tencent.wechat-pay`） | `@EnableConfigurationProperties` |
| 开放平台配置 | `TencentWechatOpenPlatformProperties`（前缀 `plugin.tencent.open-platform`） | 同上 |
| 小程序配置 | `TencentMiniAppProperties`（前缀 `plugin.tencent.mini-app`） | 同上（**仅声明，无消费方**） |
| 客户端工厂 | `TencentWechatPayClientFactory` | 7 个 service 工厂 + `NotificationParser` + `HttpClient` |
| 支付网关 SPI | `TencentWechatPayGateway`（24 个方法） | `ServiceProvider.of(TencentWechatPayGateway.class)` |
| 默认实现 | `DefaultTencentWechatPayGateway`（`@Spi({"default","wechat-pay"})`） | 扩展名 `default` / `wechat-pay` |
| epay 实现 | `EpayTencentWechatPayGateway`（`@Spi({"epay"})`） | 扩展名 `epay` |
| 下单 / 查询 DTO | `TencentWechatPayRequest`、`TencentWechatPayResponse`、`TencentWechatOrderResponse` | 五种下单共用 |
| 退款 | `TencentWechatRefundRequest`、`TencentWechatRefundResponse`、`TencentWechatRefundNotifyPayload` | 退款与退款通知 |
| 支付分 | `TencentWechatPayScoreRequest`、`TencentWechatPayScoreResponse`、`TencentWechatPayScoreNotifyPayload` | 创建 / 查询 / 完结 / 撤销 / 通知 |
| 通知解析 | `TencentWechatNotifyRequest`、`TencentWechatPayNotifyPayload` | `parsePayNotify`、`parseRefundNotify` |
| 登录集成 | `WechatMiniUserInfoService`（`@Spi("WECHAT_MINI")`） | `META-INF/extensions/...UserInfoService` |
| 开放平台服务 | `WechatOpenPlatformService`、`OpenIdConvertService`、`DefaultWechatOpenPlatformService`、`WechatOpenIdConvertService` | **空占位接口**，无方法 |
| 服务信息 | `tencentServiceInfoContributor` | 启动横幅 `tencent` 标志位 |

合计 25 个类，其中真正被业务调用的是网关（`TencentWechatPayGateway` + 默认实现）
与 `WechatMiniUserInfoService`；`EpayTencentWechatPayGateway` 只是 `"epay"` 扩展名的别名
（直接 `extends DefaultTencentWechatPayGateway`，无独立逻辑）。

### 1.1 TencentWechatPayGateway

| 分组 | 方法 |
|---|---|
| JSAPI | `jsapiPay`、`queryJsapiOrder`、`closeJsapiOrder` |
| H5 | `h5Pay`、`queryH5Order`、`closeH5Order` |
| Native | `nativePay`、`queryNativeOrder`、`closeNativeOrder` |
| APP | `appPay`、`queryAppOrder`、`closeAppOrder` |
| 小程序 | `miniProgramPay`、`queryMiniProgramOrder`、`closeMiniProgramOrder` |
| 退款 | `refund`、`queryRefund` |
| 通知 | `parsePayNotify`、`parseRefundNotify` |
| 支付分 | `createPayScoreOrder`、`queryPayScoreOrder`、`completePayScoreOrder`、`cancelPayScoreOrder`、`parsePayScoreNotify` |

约定：

- 所有方法**第一个参数都是 `TencentWechatPayProperties`**，网关本身无状态、不持有配置。
- `TencentWechatPayResponse` 组件为 `success` / `payUrl` / `sdkParams` / `message` /
  `rawResponse`：H5 与 Native 下单产出 `payUrl`，JSAPI / 小程序 / APP 产出 `sdkParams`
  （可直接交给前端 `WeixinJSBridge` / 小程序 `wx.requestPayment`）。
- `TencentWechatPayRequest` 组件为 `orderNo` / `amountFen`（**单位分，Long**）/
  `currency` / `description` / `notifyUrl` / `expireTime` / `payerOpenId` / `clientIp` /
  `deviceId` / `attach` / `h5Type` / `appName` / `appUrl`。
- 下单与退款都按 `firstNonBlank(request.notifyUrl(), properties.getNotifyUrl())` 取通知地址，
  请求级参数优先。
- 通知解析走微信 SDK 的 `NotificationParser`（`apiV3Key` 验签 + 解密），签名头由
  `TencentWechatNotifyRequest` 的 5 个字段（`serialNumber` / `timestamp` / `nonce` /
  `signature` / `signType`）+ `body` 传入。

### 1.2 WechatMiniUserInfoService（oauth 侧登录）

`checkLogin(principal, credential, address, ext)`：

- `principal` = 小程序 `openid`，为空返回带错误信息的 `UserResume`。
- `ext` 是 `me.zhyd.oauth.model.AuthUser` 时补 `username`，并取 `authUser.getUuid()`
  作为 `unionId`；否则 `username` / `unionId` 都退化为 `openid`。
- `credential` 非空时当作手机号写入 `resume.phone`。
- `upgrade(UserResult)` 恒返回 `null`。

### 1.3 声明但未实现的组件

| 组件 | 实际状态 |
|---|---|
| `WechatOpenPlatformService` / `OpenIdConvertService` | 只有接口声明，**无任何方法**（源码注释写明「目前仅作为占位符」） |
| `DefaultWechatOpenPlatformService` / `WechatOpenIdConvertService` | 空实现类，构造函数只打日志；`open-platform.enable=true` 时才会装配 |
| `TencentMiniAppProperties` | 三个 `@ConfigurationProperties` 之一，**没有任何代码读取** `appId` / `appSecret` |
| `SandboxWechatHttpClient` | 类存在但全仓库无引用，沙箱通道未接入 |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-tencent-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `com.github.wechatpay-apiv3:wechatpay-java` | 微信支付 SDK（含 payments / refund / payscore） | 版本由父 pom 的 `wechatpay.version` 管理 |
| `com.github.wechatpay-apiv3:wechatpay-java-core` | `Config`、`NotificationParser`、`HttpClient` | 与上者同版本 |
| `com.chua:spring-support-oauth-client-starter` | `UserInfoService`、`UserResume` | 必需，直接传递 |
| `com.chua:utils-support-common-starter` | `ServiceProvider`、`ServiceInfoContributor` | 必需 |
| `com.fasterxml.jackson.core:jackson-databind` | 报文解析 | 必需 |
| `lombok`（provided） | `@Data` | 编译期 |

## 三、配置项

### 3.1 微信支付 `plugin.tencent.wechat-pay`

| key | 默认值 | 说明 |
|---|---|---|
| `app-id` | 空 | 公众号 / 小程序 / APP 的 AppID（下单 `appid` 字段） |
| `merchant-id` | 空 | 商户号 |
| `private-key` | 空 | 商户 API 私钥（`RSAAutoCertificateConfig` 用） |
| `merchant-serial-number` | 空 | 商户证书序列号 |
| `api-v3-key` | 空 | APIv3 密钥，通知解密与验签 |
| `notify-url` | 空 | 默认通知地址，请求级 `notifyUrl` 优先 |
| `enable` / `enabled` | `false` | **两个字段都未被读取**：`TencentConfiguration` 没有按支付配置做条件装配 |
| `sandbox` | `false` | 声明项，网关未读取（沙箱 HTTP 客户端未接入） |
| `sandbox-sign-key` | 空 | 声明项，同上 |

### 3.2 微信开放平台 `plugin.tencent.open-platform`

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 为 `true` 才装配 `WechatOpenPlatformService` / `OpenIdConvertService`（两者均为空实现） |
| `component-app-id` | 空 | 第三方平台 AppID，仅用于日志 |
| `component-app-secret` | 空 | 第三方平台 AppSecret，仅用于日志 |
| `default-mini-app-id` | 空 | 默认小程序 AppID，仅用于日志 |
| `storage-path` | `wechat-openid-mapping.json` | 声明项，无持久化实现 |

### 3.3 小程序 `plugin.tencent.mini-app`

`enable` / `enabled` / `app-id` / `app-secret` 全部**声明未消费**：本模块没有小程序登录服务，
登录只走 oauth-client 的 `UserInfoService`（`WECHAT_MINI`）。

## 四、快速开始

**第一步**：引入依赖（见第二节），确认宿主已具备商户 API 证书私钥文件内容
（用于 `private-key`，非文件路径）。

**第二步**：配置商户参数：

```yaml
plugin:
  tencent:
    wechat-pay:
      app-id: wx1234567890abcdef
      merchant-id: "1900000001"
      merchant-serial-number: "5157F09EFDC096DE15EBE81A47057A7232F1B8E1"
      private-key: ${WECHAT_MCH_PRIVATE_KEY}
      api-v3-key: ${WECHAT_API_V3_KEY}
      notify-url: https://example.com/pay/wechat/notify
```

**第三步**：取网关下单。网关是 SPI 不是 Bean：

```java
TencentWechatPayGateway gateway =
        ServiceProvider.of(TencentWechatPayGateway.class).getExtension("wechat-pay");
```

## 五、运行示例

### 5.1 调用示例

运行期 API 为**步骤式**：所有 DTO 都是 `record`，调用形态固定为
「配置 + DTO → 响应 record」。本模块**无链式 API**（唯一链式调用在内部走微信 SDK 的
`RSAAutoCertificateConfig.Builder`，不对业务暴露）。

**JSAPI 下单 → 前端调起支付 → 通知验签**：

```java
@Service
public class WechatPayService {

    private final TencentWechatPayProperties properties;
    private final TencentWechatPayGateway gateway =
            ServiceProvider.of(TencentWechatPayGateway.class).getExtension("wechat-pay");

    public WechatPayService(TencentWechatPayProperties properties) {
        this.properties = properties;
    }

    /** 返回可直接交给 WeixinJSBridge 的参数 */
    public Map<String, Object> jsapiPay(String orderNo, long amountFen, String openId, String clientIp) {
        TencentWechatPayResponse resp = gateway.jsapiPay(properties,
                new TencentWechatPayRequest(
                        orderNo, amountFen, "CNY", "订单 " + orderNo,
                        "https://example.com/pay/wechat/notify",   // 请求级 notify_url
                        null,                                       // expireTime 为空则用平台默认
                        openId, clientIp, null, null, null, null, null));
        if (!resp.success()) {
            throw new IllegalStateException(resp.message());
        }
        return resp.sdkParams();
    }

    /** 支付通知：请求头 5 个字段 + 原始 body 一起传进来 */
    @PostMapping("/pay/wechat/notify")
    public String notify(@RequestHeader("Wechatpay-Serial") String serial,
                         @RequestHeader("Wechatpay-Timestamp") String timestamp,
                         @RequestHeader("Wechatpay-Nonce") String nonce,
                         @RequestHeader("Wechatpay-Signature") String signature,
                         @RequestHeader("Wechatpay-Signature-Type") String signType,
                         @RequestBody String body) {
        TencentWechatPayNotifyPayload payload = gateway.parsePayNotify(properties,
                new TencentWechatNotifyRequest(serial, timestamp, nonce, signature, signType, body));
        // 按 payload 里的 out_trade_no 幂等更新订单
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }
}
```

- **H5**：`h5Pay(...)`，取 `resp.payUrl()` 直接 302。
- **Native**：`nativePay(...)`，`resp.payUrl()` 即二维码内容（`code_url`）。
- **小程序**：`miniProgramPay(...)`，`payerOpenId` 填小程序 openid，取 `sdkParams`。
- **APP**：`appPay(...)`，取 `sdkParams` 交给客户端 SDK。

**支付分（先享后付）**：`createPayScoreOrder` → `queryPayScoreOrder` →
`completePayScoreOrder` / `cancelPayScoreOrder`，通知用 `parsePayScoreNotify`。

### 5.2 SPI 扩展示例

新增一个支付网关实现（例如接第三方聚合支付通道），遵循「实现 → 登记 → 生效」：

**① 实现**：实现 `TencentWechatPayGateway` 的 24 个方法，不支持的直接抛
`UnsupportedOperationException`（本模块的 `EpayTencentWechatPayGateway` 是另一种做法：
直接继承默认实现、只换扩展名，不覆写任何方法）：

```java
@Spi("acme")
public class AcmeTencentWechatPayGateway implements TencentWechatPayGateway {
    @Override
    public TencentWechatPayResponse jsapiPay(TencentWechatPayProperties properties,
                                             TencentWechatPayRequest request) {
        // 自定义通道
        throw new UnsupportedOperationException("聚合通道暂不支持 jsapi 下单");
    }
    // 其余 23 个方法同理
}
```

**② 登记**：在 `src/main/resources/META-INF/extensions/` 下建
`com.chua.starter.tencent.support.payment.TencentWechatPayGateway` 文件，写入实现类全名
（一行一个）；`@Spi` 的值就是扩展名。

**③ 生效**：调用方改用 `getExtension("acme")`；`getExtension("default")` 仍指向
`DefaultTencentWechatPayGateway`，`getExtension("epay")` 指向 epay 实现。

开放平台侧扩展：实现 `OpenIdConvertService`（当前接口无方法，需先补契约）并在
`TencentConfiguration` 之外自行提供 Bean；`open-platform.enable=true` 只会装配
本模块的两个空实现，`@ConditionalOnMissingBean` 会让位给宿主自定义 Bean。

## 六、与其它 starter 的关系

- **`spring-support-oauth-client-starter`（必需）**：提供 `UserInfoService` 契约，
  小程序登录通过 `META-INF/extensions` 自动发现（`WECHAT_MINI`）。
- **`utils-support-common-starter`（必需）**：`ServiceProvider` 注册表 + `ServiceInfoContributor`。
- **与 `spring-support-aliyun-starter` 平行**：同为「支付网关 SPI + 登录实现」结构，
  互不依赖，可只引其一；两者都用 `ServiceProvider` 按扩展名取实现。

## 七、已知限制

1. **无短信、无 COS、无公众号推送、无小程序码**：本模块只有微信支付网关与小程序登录；
   需要这些能力请直接引腾讯云 / 微信官方 SDK。
2. **小程序登录只是「用户信息映射」**：`WechatMiniUserInfoService` 不做 `code2Session`，
   也不校验 `code` / `session_key`；它只把已有的 `openid` 转成 `UserResume`。
   `plugin.tencent.mini-app.*` 配置全部无消费方。
3. **沙箱未接入**：`sandbox` / `sandbox-sign-key` 无代码读取，`SandboxWechatHttpClient`
   无引用；切沙箱不会生效，网关固定走 `https://api.mch.weixin.qq.com`。
4. **`enable` / `enabled` 是死开关**：支付与小程序的开关字段都没有 `@ConditionalOnProperty`
   读取，配不配都不影响网关可用性。
5. **开放平台服务是空壳**：`WechatOpenPlatformService` / `OpenIdConvertService` 无方法，
   两个实现类只打日志；`storage-path` 声明的 JSON 持久化未实现。
6. **无 Controller、无持久化、无对账**：本模块不提供回调端点、订单表、退款单表、
   账单下载与对账任务，通知端点的幂等与状态机需业务侧实现。
7. **每次调用重新建 `Config`**：网关内部每个方法都 `createAutoCertificateConfig(properties)`，
   没有缓存 `Config` / service，高频调用会重复构建自动证书配置。
8. **支付分依赖商户权限**：`*PayScore*` 系列需要单独开通支付分产品，未开通时微信侧直接返回错误码，
   模块不做前置探测。
