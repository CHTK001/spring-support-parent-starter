# spring-support-payment-starter

## 1. 模块定位

`spring-support-payment-starter` 是当前支付主线模块，用于承载：

- 商户管理
- 支付渠道管理
- 支付订单与状态机
- 退款单与交易流水
- 钱包账户与钱包订单
- 微信支付分订单
- 支付回调与运营台能力

历史目录 `spring-api-support-common-payment-starter` 仅保留迁移存档，不再作为当前接入主线。

前端联调仓位于：

- `g:/work/vue-support-parent-starter/apps/vue-support-payment-starter`
- 公共支付页面包：`g:/work/vue-support-parent-starter/pages/pay`

## 2. 能力边界

当前后端已提供以下接口域：

- 商户：`/api/merchant`
- 渠道：`/api/channel`
- 商户扩展配置：`/api/merchant/{merchantId}/payment-config`
- 商户钱包限额：`/api/merchant/{merchantId}/wallet-limit`
- 订单：`/api/order`
- 退款：`/api/refund`
- 交易流水：`/api/transaction`
- 钱包账户：`/api/wallet`
- 钱包订单：`/api/wallet/order`
- 微信支付分：`/api/wechat/payscore/order`
- 支付通知：`/api/notify/**`
- 运营台：`/api/ops/**`
  - 首页业务汇总：`/api/ops/dashboard/summary`

## 3. Maven 接入

```xml
<dependency>
  <groupId>com.chua</groupId>
  <artifactId>spring-support-payment-starter</artifactId>
  <version>${revision}</version>
</dependency>
```

如果你的服务只是业务方，不负责统一支付运营台，可以关闭运营接口和内置调度：

```yaml
plugin:
  payment:
    scheduler:
      enabled: false
    ops:
      enabled: false
```

## 4. 数据库初始化

初始化脚本：

- `src/main/resources/db/init/V1.0__init_payment.sql`

该脚本会创建支付主表、商户、渠道、钱包、支付分等运行所需结构。

## 5. 关键配置

### 5.1 基础配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/payment
    username: root
    password: your-password

plugin:
  payment:
    callback:
      base-url: https://pay.example.com
    scheduler:
      enabled: true
      engine: internal
    ops:
      enabled: true
    provider:
      default-spi: default
      wechat-spi: default
      alipay-spi: default
    security:
      key: PaymentSystem16
```

### 5.2 配置说明

- `plugin.payment.callback.base-url`
  用于生成默认回调地址，必须配置为业务侧可访问的支付服务根地址。
- `plugin.payment.scheduler.enabled`
  是否启用支付调度任务。
- `plugin.payment.scheduler.engine`
  默认 `internal`；若接入任务平台，可切到外部调度方案。
- `plugin.payment.ops.enabled`
  是否暴露 `/api/ops/**` 运营接口。
- `plugin.payment.provider.*-spi`
  配置默认 provider SPI，渠道扩展字段仍可覆盖。
- `plugin.payment.security.key`
  支付敏感字段加密密钥；未配置时兼容历史默认值 `PaymentSystem16`。

## 6. 回调约定

推荐使用路径携带业务号的 scoped 回调：

- 微信支付：`/api/notify/wechat/pay/{channelId}/{orderNo}`
- 微信退款：`/api/notify/wechat/refund/{channelId}/{refundNo}`
- 微信支付分：`/api/notify/wechat/payscore/{channelId}/{outOrderNo}`
- 支付宝支付：`/api/notify/alipay/pay/{channelId}/{orderNo}`
- 钱包订单：`/api/notify/wallet/{orderType}/{orderNo}`

注意：

- 显式请求参数中的 `notifyUrl` 优先级高于默认生成地址。
- 渠道与商户维度的 notify 配置也可能覆盖 scoped 默认地址。

## 7. 启动建议

### 7.1 后端本地验证

2026-04-11 实测可通过测试类路径直接启动：

- 主类：`com.chua.payment.support.PaymentTestApplication`
- 端口：`18080`
- 测试数据源：H2 内存库

推荐命令：

```bash
mvn -f g:/work/spring-support-parent-starter/spring-support-payment-starter/pom.xml \
  -DskipTests \
  -Dexec.mainClass=com.chua.payment.support.PaymentTestApplication \
  -Dexec.classpathScope=test \
  "-Dexec.args=--server.port=18080 --plugin.payment.callback.base-url=http://127.0.0.1:18080" \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

### 7.2 前端联调

支付前端开发目录：

- `g:/work/vue-support-parent-starter/apps/vue-support-payment-starter`

本次联调过程中发现该前端当前并非零配置即开，需要保证以下修复已存在：

- `vite.config.ts` 不再依赖旧版 `@repo/build-config` dist 链式 API
- `@pages/common` alias 已补齐
- `pages/pay/src/index.ts` 不再导出不存在的 `index.vue`
- `src/main.ts` 改为支付台独立挂载，而不是 `@repo/core` 通用壳

## 8. 浏览器联调状态

### 2026-04-11 实测结果

后端：

- `http://127.0.0.1:18080` 可正常监听

前端：

- `http://127.0.0.1:4174` 可启动
- 左侧支付运营台壳层可渲染

当前阻塞：

- `router-view` 内业务主体页面仍未稳定渲染
- 因此前端“商户 -> 渠道 -> 订单 -> 退款/流水”完整浏览器链路尚未闭环

建议：

- 优先继续排查 `vue-support-payment-starter` 的页面主体渲染问题
- 后端业务能力可先通过单元测试和接口联调继续推进

## 9. 验证记录

详细测试结论见：

- `./业务单元测试报告.md`

## 10. 当前结论

- 后端支付主链路的业务单测已通过
- 前端支付台的“可启动性”问题已定位并修复一部分
- 浏览器业务链路目前还缺最后一段页面主体渲染修复
