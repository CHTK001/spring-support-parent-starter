# spring-support-payment-starter

支付系统服务端 starter：承载商户管理、支付渠道配置、支付订单与退款、钱包账户与订单、微信支付分、第三方异步通知回调以及支付运营台等能力。
模块不提供全局开关，引入依赖并配合数据源后即通过自动配置装配（调度与运营台能力可单独关闭）。

## 一、模块能力

本模块为单 Maven 模块，无子模块；渠道能力由 `com.chua.payment.support.channel.impl` 包内的 10 个渠道实现类承载，
实际网关调用通过提供方 SPI（`AliyunAlipayGateway` / `TencentWechatPayGateway`）分发到 `spring-support-aliyun-starter` 与 `spring-support-tencent-starter`。

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置 | `PaymentAutoConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 商户管理 | `MerchantService` / `MerchantController` | `GET/POST /merchant` |
| 渠道管理 | `MerchantChannelService` / `MerchantChannelController` | `/channel`（含 `/channel/catalog` 支付方式目录） |
| 商户支付配置 | `MerchantPaymentConfigService` | `/merchant/{merchantId}/payment-config`（`MerchantSettingController`） |
| 订单管理 | `PaymentOrderService` / `OrderController` | `/order`（创建/支付/同步/取消/退款/完成） |
| 退款单管理 | `PaymentRefundOrderService` / `RefundOrderController` | `/refund` |
| 交易流水 | `TransactionRecordService` / `TransactionController` | `/transaction` |
| 钱包账户 | `WalletAccountService` / `WalletAccountController` | `/wallet` |
| 钱包订单 | `WalletOrderService` / `WalletOrderController` | `/wallet/order`（充值/转账/提现） |
| 提现审批 | `WalletWithdrawApprovalService` / `WalletWithdrawApprovalController` | `/wallet/withdraw-approval` |
| 微信支付分 | `WechatPayScoreOrderService` / `WechatPayScoreOrderController` | `/wechat/payscore/order` |
| 账户自助 API | `CurrentAccountOrderController` / `CurrentAccountWalletController` / `MpWalletController` | `/account`、`/account/wallet`、`/mp/wallet` |
| 支付/退款/支付分回调 | `PaymentNotifyController`、`WalletNotifyController` | `/notify/**`（含 `/notify/wallet/**`） |
| 支付运营台 | `PaymentOpsController` / `PaymentDashboardService` | `/ops/**`（`plugin.payment.ops.enabled=true`） |
| 订单状态机 | `OrderStateMachineConfig` / `OrderStateMachineService` | Spring State Machine（`@EnableStateMachineFactory`） |
| 支付通道注册表 | `PaymentChannelRegistry` | 按 `channelType + channelSubType` 路由 `PaymentChannel` Bean |
| 提供方网关 SPI | `PaymentProviderGatewayRegistry` | `ServiceProvider` 加载支付宝/微信网关扩展 |
| 敏感字段加解密 | `PaymentCipherService`（AES + Base64） | 渠道 AppKey/私钥落库前加密 |
| 内置调度任务 | `PaymentTaskSchedulerManager` + `PaymentManagedTask` 实现 | `plugin.payment.scheduler.enabled=true` 且 `engine=internal` |
| 事件与日志 | `PaymentEventListener` / `PaymentEvent` 系列 | `plugin.payment.event.log-enabled=true` |
| OAuth 白名单 | `PaymentOauthWhitelistConfiguration` | 存在 `AuthClientProperties` 时反射合并 `/api/notify/**` |
| 服务信息登记 | `paymentServiceInfoContributor` | 控制台模块能力 |

### 渠道实现清单（channel.impl）

| 渠道类型 | 渠道子类型 | 关键类 | 网关方法 |
|:---------|:-----------|:-------|:---------|
| `WECHAT` / `EPAY` | `JSAPI` | `WechatJsapiPaymentChannel` | `TencentWechatPayGateway#jsapiPay` |
| `WECHAT` / `EPAY` | `NATIVE` | `WechatNativePaymentChannel` | `#nativePay` |
| `WECHAT` / `EPAY` | `MINI_PROGRAM` / `MINIPROGRAM` | `WechatMiniProgramPaymentChannel` | `#miniProgramPay` |
| `WECHAT` / `EPAY` | `H5` | `WechatH5PaymentChannel` | `#h5Pay` |
| `WECHAT` / `EPAY` | `APP` | `WechatAppPaymentChannel` | `#appPay` |
| `ALIPAY` | `WEB` | `AlipayWebPaymentChannel`（`FAST_INSTANT_TRADE_PAY`） | `AliyunAlipayGateway#pagePay` |
| `ALIPAY` | `WAP` | `AlipayWapPaymentChannel`（`QUICK_WAP_WAY`） | `#wapPay` |
| `ALIPAY` | `APP` | `AlipayAppPaymentChannel`（`QUICK_MSECURITY_PAY`） | `#appPay` |
| `WALLET` | `BALANCE` | `WalletBalancePaymentChannel` | 本地 `WalletAccountService#pay/refund` |
| `COMPOSITE` | `AGGREGATE_ROUTE` | `CompositeAggregateRoutePaymentChannel` | 按 extConfig `targetChannelId` 委派目标渠道 |

渠道类型枚举见 `PaymentChannelType`（微信支付/支付宝/易支付/运营网关/钱包）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-payment-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-boot-starter-web` | Web 容器与 REST 控制器 |
| `spring-support-common-starter` | `ServiceInfoContributor`、统一返回/异常协议 |
| `mybatis-plus-spring-boot4-starter` / `mybatis-plus-jsqlparser` | 持久层与分页拦截器 |
| `spring-support-mybatis-starter` / `spring-support-datasource-starter` | Mapper 装配与数据源 |
| `spring-support-redis-starter` | Redis（模块内仅声明 `RedisTemplate` 序列化方案，业务代码未使用） |
| `spring-support-swagger-starter` | OpenAPI 文档标注（`@Tag`/`@Operation`） |
| `spring-support-oauth-client-starter` | `@Permission` 鉴权与回调接口 OAuth 白名单 |
| `spring-support-tencent-starter` | 微信支付网关 SPI（`TencentWechatPayGateway`） |
| `spring-support-aliyun-starter` | 支付宝网关 SPI（`AliyunAlipayGateway`） |
| `spring-support-job-starter` | `@Job` 注解（`engine=job` 时接入调度平台） |
| `spring-statemachine-core` / `spring-statemachine-starter` | 订单状态机 |
| `jackson-dataformat-xml` | 微信/支付宝报文解析辅助 |
| `utils-support-springboot-starter` | 策略/容错能力（`spring-support-strategy-starter` 已下线） |

版本由父 POM `spring-boot-starter-parent:4.1.0`（Java 25）统一管理；本模块通过父 POM 的 `4.0.0.51` 传递版本。

## 三、配置项

配置前缀共 6 段：`plugin.payment.provider`、`plugin.payment.callback`、`plugin.payment.scheduler`、
`plugin.payment.ops`、`plugin.payment.security`（另兼容 `payment.encryption`）、`plugin.payment.event`。

### 3.1 提供方 SPI（`plugin.payment.provider`）

来源：`PaymentProviderProperties`（`@ConfigurationProperties(prefix = "plugin.payment.provider")`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.payment.provider.default-spi` | `default` | 全局兜底 provider SPI 标识（`AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI`） |
| `plugin.payment.provider.alipay-spi` | `default` | 支付宝渠道 SPI（可选 `default`、`alipay` 等） |
| `plugin.payment.provider.wechat-spi` | `default` | 微信渠道 SPI（可选 `default`、`wechat-pay`、`epay` 等） |
| `plugin.payment.provider.epay-spi` | `epay` | 易支付渠道 SPI |

渠道级 `extConfig.providerSpi`（或 `providerExtension`/`spi`）优先级高于全局配置（见 `AbstractMerchantPaymentChannel#providerSpi`）。

### 3.2 回调地址（`plugin.payment.callback`）

来源：`PaymentCallbackProperties`（`@ConfigurationProperties(prefix = "plugin.payment.callback")`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.payment.callback.base-url` | 无默认值 | 对外可访问的支付服务根地址，如 `https://pay.example.com`；仅作为 `pay_global_config.paymentNotifyBaseUrl` 的兜底初始化值，未配置时无法生成 scoped 默认回调地址 |

### 3.3 调度（`plugin.payment.scheduler`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.payment.scheduler.enabled` | `true` | 启用内置调度任务与动态调度管理器（`matchIfMissing=true`） |
| `plugin.payment.scheduler.engine` | `internal` | `internal` 使用模块内置线程池调度；`job` 接入 spring-support-job-starter 任务平台 |
| `plugin.payment.scheduler.platform-base-url` | `http://127.0.0.1:18083/scheduler/api` | `engine=job` 时调度平台根地址（代码 `PaymentPlatformSchedulerTaskOpsService#baseUrl`） |
| `plugin.payment.scheduler.platform-namespace` | `payment` | 调度平台命名空间 |
| `plugin.payment.scheduler.platform-access-mode` | `auto` | `auto` 按请求是否携带认证头切换 `platform`/`console`；可强制配置 `platform` 或 `console` |
| `plugin.payment.scheduler.platform-console-username` | `admin` | 轻量控制台登录账号 |
| `plugin.payment.scheduler.platform-console-password` | 见说明 | 代码默认 `${payment.console.password:}`；`additional-spring-configuration-metadata.json` 声明默认 `admin123456`，两处不一致，需以实际运行环境配置为准 |

内置任务默认 Cron（`PaymentManagedTask` 实现，engine=internal 时写入 `pay_scheduler_config`）：

| taskKey | 默认 Cron | 任务说明 |
|:--------|:----------|:---------|
| `payment-order-timeout-scan` | `0 */10 * * * ?` | 按商户支付配置扫描超时订单并自动取消 |
| `payment-notify-retry` | `0 */5 * * * ?` | 扫描待重试回调异常并回放 |
| `payment-notify-cleanup` | `0 0 2 * * ?` | 清理已解决且超 30 天的回调异常 |
| `payment.order.partition.create` | `0 0 2 * * ?` | 订单分表创建 |
| `payment.order.partition.migrate` | `0 30 2 * * ?` | 订单分表数据迁移 |
| `payment.transaction.partition.create` | `0 0 3 * * ?` | 流水分表创建 |
| `payment.transaction.partition.migrate` | `0 30 3 * * ?` | 流水分表数据迁移 |

### 3.4 运营台（`plugin.payment.ops`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.payment.ops.enabled` | `true` | 是否暴露 `/ops/**` 运营接口（`matchIfMissing=true`）；非支付中心服务可关闭 |

### 3.5 加密（`plugin.payment.security` / 兼容 `payment.encryption`）

来源：`PaymentCipherService`（AES/ECB + Base64，密钥归一化为 16 字节 UTF-8）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.payment.security.key` | 无（回退内置密钥） | 当前加密密钥；备选键名 `plugin.payment.security.encryption-key`、`plugin.payment.security.encryptionKey`、`payment.encryption.key` 等 |
| `plugin.payment.security.legacy-keys` | 无 | 历史兼容密钥列表（逗号分隔），解密时按「当前密钥 → 历史列表 → 内置密钥」依次尝试 |
| 内置兼容密钥 | `PaymentSystem16` | `LEGACY_DEFAULT_KEY`，未配置任何密钥时的加密/解密兜底 |

### 3.6 事件日志（`plugin.payment.event`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.payment.event.log-enabled` | `true` | `PaymentEventListener` 是否打印订单创建/支付成功/退款成功/回调接收/回调失败事件日志 |

## 四、快速开始

**第一步：引入依赖**（见「二、依赖与引入」），并保证已配置数据源与 Mapper 扫描（模块自动配置自带
`@MapperScan("com.chua.payment.support.mapper")`）。

**第二步：执行建表与种子数据脚本**（`spring-boot-sql` 初始化或手动执行）：

- `src/main/resources/db/init/V2.0__init_all_payment.sql`（全部表结构，如 `pay_merchant`、`pay_merchant_channel`、`pay_order`、`pay_transaction_record`、`pay_wallet_account`、`pay_global_config` 等）
- `src/main/resources/db/init/V2.0__initdata_payment.sql`（种子数据）

**第三步：配置 yaml**

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/payment
    username: root
    password: your-password

plugin:
  payment:
    callback:
      base-url: https://pay.example.com     # 对外回调根地址，必填
    provider:
      default-spi: default
      alipay-spi: default
      wechat-spi: default
    scheduler:
      enabled: true
      engine: internal
    ops:
      enabled: true
    security:
      key: Your16ByteSecret                # 渠道敏感字段加密密钥，未配置时使用内置 PaymentSystem16
```

业务方服务（非支付运营中心）可关闭内置调度与运营接口：

```yaml
plugin:
  payment:
    scheduler:
      enabled: false
    ops:
      enabled: false
```

**第四步：注册渠道实现 Bean 并验证**

渠道实现类（`channel.impl`）不在模块内自动装配（详见「七、已知限制」），宿主需将用到的渠道注册为 Bean，
例如：

```java
@Configuration
public class PaymentChannelBeans {
    @Bean public PaymentChannel wechatNative(MerchantChannelService s, ObjectMapper m, PaymentProviderGatewayRegistry r) {
        return new WechatNativePaymentChannel(s, m, r);
    }
    @Bean public PaymentChannel alipayWeb(MerchantChannelService s, ObjectMapper m, PaymentProviderGatewayRegistry r) {
        return new AlipayWebPaymentChannel(s, m, r);
    }
}
```

启动成功后日志可见模块能力登记（由 `paymentServiceInfoContributor` 输出），随后：

1. 创建商户：`POST /merchant`；
2. 创建渠道：`POST /channel`（渠道类型/子类型见上表，微信渠道还需 extConfig 提供 `merchantSerialNumber`）；
3. 创建订单：`POST /order`；
4. 发起支付：`POST /order/{id}/pay`（JSAPI 等需传 `payerOpenId`，或实现 `UserOpenIdResolver`）；
5. 回调地址按 scoped 约定构造（见 `PaymentCallbackUrlResolver`）：
   - 微信支付 `/api/notify/wechat/pay/{orderNo}/{merchantId}`
   - 支付宝支付 `/api/notify/alipay/pay/{orderNo}/{merchantId}`
   - 微信退款 `/api/notify/wechat/refund/{refundNo}/{merchantId}`
   - 钱包 `/api/notify/wallet/{orderType}/{orderNo}`
   - 支付分 `/api/notify/wechat/payscore/{channelId}/{outOrderNo}`

> 控制器本身映射在 `/order`、`/notify/**` 等路径（无 `/api` 前缀）；OAuth 白名单与默认回调地址按
> `/api/notify/**` 约定，宿主可设置 `server.servlet.context-path=/api` 对齐。

## 五、运行示例

### 5.1 调用示例

```bash
# 创建订单（金额单位为元）
curl -X POST http://127.0.0.1:8080/order \
  -H 'Content-Type: application/json' \
  -d '{"merchantId": 1, "channelId": 10, "orderAmount": 99.50, "subject": "测试订单", "businessOrderNo": "BIZ-001"}'

# 发起真实支付（微信 JSAPI 需 payerOpenId）
curl -X POST http://127.0.0.1:8080/order/100/pay \
  -H 'Content-Type: application/json' \
  -d '{"payerOpenId": "oXXXX"}'

# 同步第三方支付状态
curl -X POST http://127.0.0.1:8080/order/100/sync

# 运营台：查询任务列表
curl http://127.0.0.1:8080/ops/scheduler/tasks

# 手动触发回调重试
curl -X POST http://127.0.0.1:8080/ops/notify/error/{id}/retry
```

订单状态机（`OrderState`）：`PENDING → PAYING → PAID → COMPLETED`，支路 `TIMEOUT`、`CANCELLED`、`FAILED`、
`REFUNDING → REFUNDED` 及退款失败回退 `PAID`。金额为 0 的订单在 `payOrder` 时直接标记支付成功
（第三方单号形如 `ZERO_AMOUNT_<orderNo>`）。

### 5.2 SPI 扩展示例

**1) `UserOpenIdResolver`（Spring Bean 覆盖）**：将内部 `userId` 映射为渠道 openId，未提供实现时默认恒返
`null`（回退使用请求 DTO 的 `payerOpenId`）。

```java
@Component
public class MyUserOpenIdResolver implements UserOpenIdResolver {
    @Override
    public String resolveOpenId(Long merchantId, Long userId, String channelType) {
        return userOpenIdService.find(channelType, userId);   // 自行实现映射
    }
}
```

**2) 提供方网关 SPI（`PaymentProviderGatewayRegistry`）**：支付宝/微信网关走 `ServiceProvider`
（`META-INF/extensions` 注册），实现类用 `@Spi` 声明标识；默认实现位于
`spring-support-aliyun-starter`（`@Spi({"default","alipay"})`）与 `spring-support-tencent-starter`
（`@Spi({"default","wechat-pay"})`、`@Spi({"epay"})`）。通过 `plugin.payment.provider.*-spi` 或渠道
extConfig 的 `providerSpi` 切换。

```yaml
plugin:
  payment:
    provider:
      wechat-spi: wechat-pay   # 也可指向自研网关扩展
```

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-common-starter` | 本模块依赖 | 统一返回协议、`ServiceInfoContributor`、`Permission` 注解 |
| `spring-support-mybatis-starter` / `spring-support-datasource-starter` | 本模块依赖 | Mapper/数据源/分页 |
| `spring-support-redis-starter` | 本模块依赖 | 提供 Redis 能力；模块内仅声明 `RedisTemplate` 序列化 Bean，业务未使用 |
| `spring-support-oauth-client-starter` | 本模块依赖 | `@Permission` 鉴权；`PaymentOauthWhitelistConfiguration` 向 OAuth 白名单合并 `/api/notify/**` |
| `spring-support-tencent-starter` | 本模块依赖 | 微信支付网关 SPI（`TencentWechatPayGateway`），含 `epay` 扩展 |
| `spring-support-aliyun-starter` | 本模块依赖 | 支付宝网关 SPI（`AliyunAlipayGateway`），含沙箱开关 |
| `spring-support-job-starter` | 本模块依赖 | `@Job` 注解；`scheduler.engine=job` 时 `PaymentPlatformSchedulerTaskOpsService` 代理到调度平台 |
| `spring-support-swagger-starter` | 本模块依赖 | OpenAPI 文档 |
| `spring-support-sync-starter` / `spring-support-queue-starter` | 无直接依赖 | 支付事件经 Spring 事件转队列由宿主桥接（`PaymentEventListener` 注释说明） |
| `spring-support-console-starter` | 组合使用，无依赖 | 通过 `paymentServiceInfoContributor` 展示模块能力开关 |

注意区分：`spring-support-payment-starter` 是**服务端支付业务模块**（商户/订单/渠道/回调落库为 `pay_*` 表）；
`spring-support-tencent-starter` / `spring-support-aliyun-starter` 仅提供网关 SPI 与调用方凭据封装，不承载支付业务状态。

## 七、已知限制

- **渠道实现 Bean 需宿主注册**：`channel.impl` 下 10 个实现类均未标注 `@Component`/`@Bean`，模块主代码未发现
  渠道 Bean 的自动装配入口；`PaymentChannelRegistry` 通过 `applicationContext.getBeansOfType(PaymentChannel.class)`
  收集，未注册的渠道在启用校验（`MerchantChannelServiceImpl#validateEnabledChannelSupport`）与下单/支付时都会报
  「暂不支持的支付方式」。
- **`plugin.payment.callback.base-url` 无默认值**：仅用于初始化 `pay_global_config.payment_notify_base_url`，
  不配置时 `PaymentCallbackUrlResolver` 无法生成任何 scoped 默认回调地址。
- **调度密码默认值不一致**：`PaymentPlatformSchedulerTaskOpsService` 代码默认 `${payment.console.password:}`
  （占位符原样返回），而 `additional-spring-configuration-metadata.json` 声明默认 `admin123456`；`engine=job`
  模式必须显式配置真实控制台密码，否则会按占位符字面量登录。
- **`engine=job` 依赖外部调度平台**：`PaymentPlatformSchedulerTaskOpsService` 需要 `platform-base-url` 指向的
  调度平台在线；`auto` 访问模式仅在请求携带 `x-oauth-token`/`Authorization` 头时走 `/v1/job-platform`，否则走
  轻量控制台登录（Cookie 会话）。
- **回调控制器隐藏且不二次验签**：`PaymentNotifyController`/`WalletNotifyController` 标注 `@Hidden` 与
  `@ApiReturnFormatIgnore`；签名校验由 provider 网关（`parsePayNotify`/`verifyAndParsePayNotify`）完成，
  `logNotify` 记录的 `signVerified` 初始为 `null`。
- **SCP/分账等未接入**：`pay_merchant.payment_profit_sharing_enabled`、`payment_coupon_enabled`、
  `payment_split_table_enabled` 等商户开关仅有字段与 SQL 列，代码未实现分账、优惠、自动分表逻辑
  （分表仅以独立任务组件形式存在）。
- **Redis 未参与业务**：`RedisConfig` 声明的 `RedisTemplate` 序列化方案在模块业务代码中无引用，幂等/锁未使用 Redis。
- **回调重退机制**：失败重试退避固定为 1/5/15/30/60 分钟，`maxRetryCount=5`，超过后标 `ABANDONED`；
  清理任务仅删除 `RESOLVED` 且超过 30 天的记录。
- **金额校验双端**：微信/支付宝通知处理均校验回调金额与订单金额一致，不一致直接抛异常进入重试。
- **钱包通知渠道归属**：`WalletNotifyController` 的 `channelId` 为 `null`，钱包通知按订单号定位订单、按订单类型推导
  充值/转账/提现，不依赖渠道配置。
- **微信支付分仅覆盖支付链路**：`WechatPayScoreOrderService` 仅提供创建/查询/同步/完结/取消与回调，无分账/退款；
  支付分订单独立存储（`pay_wechat_pay_score_order`），不与标准订单表混用。
- **`GlobalExceptionHandler` 接管全部异常**：`PaymentException` 统一转为 `ReturnResult.error`，参数校验异常、`BindException`、
  `IllegalArgumentException` 与兜底 `Exception` 均会按统一协议返回，宿主自定义异常处理需注意优先级。