# spring-support-aliyun-starter

阿里云/支付宝生态集成

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-aliyun-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `AliyunConfiguration` | 阿里云配置 |
| `AliyunAlipayClientFactory` | 支付宝客户端工厂 |
| `AliyunAlipayGateway` | 支付宝网关 SPI |
| `AliyunAlipayNotifyRequest` | 支付宝回调请求 |
| `AliyunAlipayPayNotifyPayload` | 支付宝支付通知解析结果 |
| `AliyunAlipayPayRequest` | 支付宝下单请求 |
| `AliyunAlipayPayResponse` | 支付宝下单响应 |
| `AliyunAlipayRefundRequest` | 支付宝退款请求 |
| `AliyunAlipayRefundResponse` | 支付宝退款响应 |
| `AliyunAlipayTradeQueryResponse` | 支付宝交易查询响应 |
| ... | 共 14 个类 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-aliyun-starter
├── utils-support-common-starter
```