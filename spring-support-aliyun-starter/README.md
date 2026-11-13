# spring-support-aliyun-starter

支付宝开放平台集成 Starter：把「下单 / 查单 / 关单 / 退款 / 退款查询 / 转账 / 异步通知验签」
收敛成一个 `AliyunAlipayGateway` SPI，同时把支付宝登录注册成 oauth-client 的
`UserInfoService` 实现（`@Spi("ALIPAY")`）。

- 包名：`com.chua.starter.aliyun.support`
- 版本：`4.0.0.51`
- 范围：**只做支付宝**。本模块没有阿里云 OSS / SMS / 短信 / 对象存储实现，类名里的
  `Aliyun` 只是历史命名。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动装配 | `AliyunConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 配置 | `AliyunAlipayProperties`（前缀 `plugin.aliyun.alipay`） | `@EnableConfigurationProperties` |
| 客户端工厂 | `AliyunAlipayClientFactory#createClient`、`#createConfig` | 返回 `com.alipay.api.AlipayClient` |
| 支付网关 SPI | `AliyunAlipayGateway`（9 个方法） | `ServiceProvider.of(AliyunAlipayGateway.class)` |
| 默认实现 | `DefaultAliyunAlipayGateway`（`@Spi({"default","alipay"})`） | 扩展点名为 `default` / `alipay` |
| 下单 DTO | `AliyunAlipayPayRequest`、`AliyunAlipayPayResponse` | 页面 / WAP / APP 三态共用 |
| 查单 / 关单 | `AliyunAlipayTradeQueryResponse` | `queryOrder`、`closeOrder` |
| 退款 | `AliyunAlipayRefundRequest`、`AliyunAlipayRefundResponse` | `refund`、`queryRefund` |
| 转账 | `AliyunAlipayTransferRequest`、`AliyunAlipayTransferResponse` | 单笔转账到支付宝账户 |
| 通知验签 | `AliyunAlipayNotifyRequest`、`AliyunAlipayPayNotifyPayload` | `verifyAndParsePayNotify` |
| 登录集成 | `AlipayUserInfoService`（`@Spi("ALIPAY")`） | `META-INF/extensions/...UserInfoService` |
| 服务信息 | `aliyunServiceInfoContributor` | 启动横幅 `aliyun` 标志位 |

合计 15 个类（3 个装配 / 工厂 / 网关 SPI + 11 个 DTO 与实现）。

### 1.1 AliyunAlipayGateway

| 方法 | 说明 |
|---|---|
| `pagePay(properties, request)` | 电脑网站支付（`alipay.trade.page.pay`），返回表单 HTML |
| `wapPay(properties, request)` | 手机网站支付（`alipay.trade.wap.pay`） |
| `appPay(properties, request)` | APP 支付（`alipay.trade.app.pay`），返回 orderString |
| `queryOrder(properties, orderNo)` | 交易查询（`alipay.trade.query`） |
| `closeOrder(properties, orderNo)` | 交易关闭（`alipay.trade.close`） |
| `refund(properties, request)` | 退款（`alipay.trade.refund`） |
| `queryRefund(properties, request)` | 退款查询（`alipay.trade.fastpay.refund.query`） |
| `verifyAndParsePayNotify(properties, request)` | 异步通知验签（`AlipaySignature.rsaCheckV1`）+ 解析 |
| `transfer(properties, request)` | 单笔转账（`alipay.fund.trans.uni.transfer`） |

约定：

- 所有方法**第一个参数都是 `AliyunAlipayProperties`**，网关本身不持有配置；
  `notifyUrl` / `returnUrl` 走 `AliyunAlipayPayRequest` 的请求级字段，
  `plugin.aliyun.alipay.notify-url` / `return-url` 当前未被网关读取。
- `verifyAndParsePayNotify` 验签失败抛 `IllegalStateException`，**不返回 null**，
  调用方需自行 `try/catch` 并按支付宝要求返回 `failure`。
- `AliyunAlipayPayResponse` 的 5 个组件固定为 `success` / `body` / `tradeNo` / `message` /
  `rawResponse`：页面支付与 WAP 支付的表单 HTML、APP 支付的 orderString 都在 `body` 里，
  调 SDK 前先判 `success`。

### 1.2 AlipayUserInfoService（oauth 侧登录）

`AlipayUserInfoService#checkLogin(principal, credential, address, ext)`：

- `principal` = 支付宝用户 ID（`userId`），为空直接返回带错误信息的 `UserResume`。
- `ext` 是 `me.zhyd.oauth.model.AuthUser` 时补 `username` / `nickName` / `email`（写入 `ext`）。
- `credential` 非空时当作手机号写入 `resume.phone`。
- `upgrade(UserResult)` 恒返回 `null`（未实现），不要依赖它做资料刷新。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-aliyun-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `com.alipay.sdk:alipay-sdk-java` | 支付宝官方 SDK | 版本由父 pom 统一管理（`alipay.sdk.version`） |
| `com.chua:spring-support-oauth-client-starter` | `UserInfoService`、`UserResume`、`@Spi` 的 oauth 侧契约 | 必需，直接传递 |
| `com.chua:utils-support-common-starter` | `ServiceProvider`、`ServiceInfoContributor` | 必需 |
| `com.fasterxml.jackson.core:jackson-databind` | `biz_content` 组装 | 必需 |
| `spring-boot-autoconfigure` | `@ConditionalOnProperty`、`@EnableConfigurationProperties` | 必需 |
| `lombok`（provided） | `@Data` | 编译期 |

## 三、配置项

前缀 `plugin.aliyun.alipay`（`AliyunAlipayProperties.PREFIX`）：

| key | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | **唯一生效的总开关**：为 `true` 才装配 `AlipayClient` Bean |
| `enable` | `false` | 历史兼容字段，当前无任何代码读取 |
| `sandbox` | `false` | `true` 且未配 `server-url` 时用 `https://openapi-sandbox.dl.alipaydev.com/gateway.do` |
| `server-url` | 空 | 显式网关地址，优先级高于 `sandbox`；为空时按 `sandbox` 取生产 / 沙箱地址 |
| `app-id` | 空 | 支付宝应用 APPID |
| `private-key` | 空 | 应用私钥 |
| `alipay-public-key` | 空 | 支付宝公钥（验签与响应解密都用它） |
| `notify-url` | 空 | 声明项，网关不读取，实际以请求参数为准 |
| `return-url` | 空 | 同上 |
| `format` | `json` | 报文格式 |
| `charset` | `UTF-8` | 字符集 |
| `sign-type` | `RSA2` | 签名类型 |
| `connect-timeout` | `10000` | 连接超时（毫秒） |
| `read-timeout` | `30000` | 读超时（毫秒） |
| `max-idle-connections` | `5` | 连接池大小 |
| `keep-alive-duration` | `300000` | 连接保活（毫秒） |

> `AlipayClient` Bean 与网关解耦：`DefaultAliyunAlipayGateway` 每次调用都通过
> `AliyunAlipayClientFactory` 按 `properties` **新建**客户端，因此 `enabled=false` 时
> 网关仍可用，只是容器里没有 `AlipayClient` 可注入。

## 四、快速开始

**第一步**：引入依赖（见第二节）。模块无建表脚本、无 Controller，不需要数据库。

**第二步**：配置应用凭据（`sandbox: true` 可直接跑沙箱）：

```yaml
plugin:
  aliyun:
    alipay:
      enabled: true
      sandbox: true
      app-id: 2021000000000000
      private-key: ${ALIPAY_PRIVATE_KEY}
      alipay-public-key: ${ALIPAY_PUBLIC_KEY}
```

**第三步**：取网关并下单。网关通过 `ServiceProvider` 按扩展名获取，不注册成 Spring Bean：

```java
AliyunAlipayGateway gateway = ServiceProvider.of(AliyunAlipayGateway.class).getExtension("alipay");
```

## 五、运行示例

### 5.1 调用示例

运行期 API 为**步骤式**：`AliyunAlipayGateway` 的方法签名是「配置 + DTO」，
DTO 全为 `record`，本模块**无链式 API**（唯一的「链」是 `builder()` 风格的
`AlipayClient` 内部构造，不在本模块）。

**电脑网站支付 + 通知验签**：

```java
@Service
public class AlipayPayService {

    private final AliyunAlipayProperties properties;
    private final AliyunAlipayGateway gateway =
            ServiceProvider.of(AliyunAlipayGateway.class).getExtension("alipay");

    public AlipayPayService(AliyunAlipayProperties properties) {
        this.properties = properties;
    }

    /** 返回可直接写回浏览器的表单 HTML */
    public String pagePay(String orderNo, BigDecimal amount) {
        AliyunAlipayPayResponse resp = gateway.pagePay(properties,
                new AliyunAlipayPayRequest(
                        orderNo, amount, "订单 " + orderNo, null,
                        "https://example.com/pay/notify",   // 请求级 notify_url
                        "https://example.com/pay/return",   // 请求级 return_url
                        "FAST_INSTANT_TRADE_PAY", null));
        if (!resp.success()) {
            throw new IllegalStateException(resp.message());
        }
        return resp.body();
    }

    /** 异步通知：参数来自 request.getParameterMap()，验签失败会抛 IllegalStateException */
    @PostMapping("/pay/notify")
    public String notify(@RequestParam Map<String, String> params) {
        try {
            AliyunAlipayPayNotifyPayload payload =
                    gateway.verifyAndParsePayNotify(properties, new AliyunAlipayNotifyRequest(params));
            // payload 内是已验签的通知内容，按 out_trade_no 幂等更新订单
            return "success";
        } catch (IllegalStateException e) {
            return "failure";
        }
    }
}
```

`AliyunAlipayGateway` 是 SPI 而非 Bean，所以业务侧不能 `@Autowired`；
需要注入时自己包一层 `@Bean` 或像上面一样在构造时取一次（网关无状态，可复用）。

**APP 支付**取 `resp.body()` 作为 `orderString` 交给客户端 SDK；**WAP 支付**同样返回表单 HTML 放在 `body`。

### 5.2 SPI 扩展示例

新增一个网关实现（例如换成自建签名通道），按「实现 → 登记 → 生效」三步：

**① 实现**：实现 `AliyunAlipayGateway` 的 9 个方法：

```java
@Spi("custom")
public class CustomAliyunAlipayGateway implements AliyunAlipayGateway {
    @Override
    public AliyunAlipayPayResponse pagePay(AliyunAlipayProperties properties,
                                           AliyunAlipayPayRequest request) {
        // 自定义实现
        throw new UnsupportedOperationException("自定义网关暂不支持 pagePay");
    }
    // 其余 8 个方法同理，不支持的直接抛 UnsupportedOperationException
}
```

**② 登记**：在 `src/main/resources/META-INF/extensions/` 下建
`com.chua.starter.aliyun.support.payment.AliyunAlipayGateway` 文件，写入实现类全名
（一行一个）。`@Spi` 的 value 即扩展名。

**③ 生效**：调用方改用 `getExtension("custom")`；`getExtension("default")` 仍返回
`DefaultAliyunAlipayGateway`。同一 `@Spi` 值重复登记会覆盖，登记后不会再走默认实现。

登录侧扩展同理：实现 `com.chua.starter.oauth.client.support.user.UserInfoService` 并用
`@Spi("<登录类型>")` 标注，登记到 `META-INF/extensions/...UserInfoService`
（本模块已登记 `AlipayUserInfoService` → `ALIPAY`）。

## 六、与其它 starter 的关系

- **`spring-support-oauth-client-starter`（必需）**：提供 `UserInfoService` 契约与 `@Spi`，
  支付宝登录通过 `META-INF/extensions` 自动被 oauth-client 发现；不使用登录能力时
  这部分是「声明了但没消费方」的状态。
- **`utils-support-common-starter`（必需）**：`ServiceProvider` 注册表 + `ServiceInfoContributor`。
- **与 `spring-support-tencent-starter` 平行**：两者都是「支付网关 SPI + 登录实现」的同构结构，
  但互不依赖，可只引其一。

## 七、已知限制

1. **每个请求新建客户端**：`DefaultAliyunAlipayGateway#client` 直接
   `clientFactory.createClient(properties)`，没有缓存 `AlipayClient`，高并发下会反复建连；
   需要连接复用时自行把 `AlipayClient` 做成 Bean 并替换网关实现。
2. **`enabled` 与 `enable` 双开关**：只有 `enabled` 被 `@ConditionalOnProperty` 读取，
   `enable` 是死配置，写成 `enable: true` 不会装配任何东西。
3. **`notify-url` / `return-url` 未消费**：网关只读请求级字段，配在 yaml 里不生效。
4. **验签异常语义**：`verifyAndParsePayNotify` 以异常表达失败（含「参数为空」「验签失败」
   「验签异常」三种），没有返回 `Optional` / 布尔，调用方必须捕获。
5. **无 Controller / 无持久化**：本模块不提供回调端点、订单表、对账任务；
   通知端点、幂等与订单状态机都要业务侧自己实现。
6. **转账无查单**：只有 `transfer`，没有转账结果查询与撤销。
7. **`AlipayUserInfoService#upgrade` 返回 `null`**：oauth-client 侧若有资料刷新流程，本实现不参与。
8. **模块命名**：包名与构件名带 `aliyun`，但**不含阿里云 OSS / 短信**等能力，
   只有支付宝开放平台；如需阿里云其它产品需另接官方 SDK。
