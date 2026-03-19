# 支付系统使用手册

## 目录
1. [快速开始](#快速开始)
2. [商户管理](#商户管理)
3. [支付渠道配置](#支付渠道配置)
4. [订单管理](#订单管理)
5. [交易流水查询](#交易流水查询)
6. [状态机说明](#状态机说明)
7. [常见问题](#常见问题)

## 快速开始

### 1. 环境准备
- JDK 21+
- MySQL 8.0+
- Redis 6.0+

### 2. 数据库初始化
```bash
mysql -u root -p payment_system < src/main/resources/db/schema.sql
```

### 3. 配置文件
在 `application.yml` 中配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_system
    username: root
    password: your_password
    
  redis:
    host: localhost
    port: 6379
    
payment:
  encryption:
    key: your-32-character-encryption-key
```

### 4. 启动应用
```bash
mvn spring-boot:run
```

## 商户管理

### 创建商户
```http
POST /api/merchant
Content-Type: application/json

{
  "merchantName": "测试商户",
  "merchantCode": "TEST001",
  "contactName": "张三",
  "contactPhone": "13800138000",
  "contactEmail": "test@example.com",
  "status": 1
}
```

### 查询商户
```http
GET /api/merchant/{id}
```

### 商户列表（分页）
```http
GET /api/merchant/page?pageNum=1&pageSize=10
```

### 更新商户
```http
PUT /api/merchant/{id}
Content-Type: application/json

{
  "merchantName": "更新后的商户名",
  "contactName": "李四"
}
```

### 激活/停用商户
```http
PUT /api/merchant/{id}/activate
PUT /api/merchant/{id}/deactivate
```

### 删除商户
```http
DELETE /api/merchant/{id}
```

## 支付渠道配置

### 配置微信支付
```http
POST /api/channel
Content-Type: application/json

{
  "merchantId": 1,
  "channelType": "WECHAT",
  "channelName": "微信支付",
  "config": {
    "appId": "wx1234567890",
    "mchId": "1234567890",
    "apiKey": "your_api_key",
    "certPath": "/path/to/cert.p12"
  },
  "status": 1
}
```

### 配置支付宝
```http
POST /api/channel
Content-Type: application/json

{
  "merchantId": 1,
  "channelType": "ALIPAY",
  "channelName": "支付宝",
  "config": {
    "appId": "2021001234567890",
    "privateKey": "your_private_key",
    "alipayPublicKey": "alipay_public_key",
    "sandbox": false
  },
  "status": 1
}
```

### 查询渠道配置
```http
GET /api/channel/{id}
GET /api/channel/merchant/{merchantId}
```

### 启用/禁用渠道
```http
PUT /api/channel/{id}/enable
PUT /api/channel/{id}/disable
```

## 订单管理

### 创建订单
```http
POST /api/order
Content-Type: application/json

{
  "merchantId": 1,
  "channelId": 1,
  "orderAmount": 100.00,
  "currency": "CNY",
  "subject": "商品购买",
  "body": "购买商品详情",
  "notifyUrl": "https://your-domain.com/notify",
  "returnUrl": "https://your-domain.com/return"
}
```

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 1,
    "orderNo": "20260318123456789012",
    "orderAmount": 100.00,
    "orderState": "PENDING",
    "createTime": "2026-03-18 12:34:56"
  }
}
```

### 查询订单
```http
GET /api/order/{id}
GET /api/order/no/{orderNo}
```

### 订单列表（分页）
```http
GET /api/order/page?pageNum=1&pageSize=10
GET /api/order/merchant/{merchantId}?pageNum=1&pageSize=10
```

### 取消订单
```http
PUT /api/order/{id}/cancel
```

### 完成订单
```http
PUT /api/order/{id}/complete
```

### 申请退款
```http
POST /api/order/{id}/refund
Content-Type: application/json

{
  "refundAmount": 50.00,
  "refundReason": "用户申请退款"
}
```

### 查询退款
```http
GET /api/order/{id}/refund
```

## 交易流水查询

### 根据ID查询流水
```http
GET /api/transaction/{id}
```

### 根据订单号查询流水
```http
GET /api/transaction/order/{orderNo}?pageNum=1&pageSize=10
```

### 根据商户ID查询流水
```http
GET /api/transaction/merchant/{merchantId}?pageNum=1&pageSize=10
```

### 流水列表（分页）
```http
GET /api/transaction/page?pageNum=1&pageSize=10
```

## 状态机说明

### 订单状态
1. **PENDING**：待支付
2. **PAYING**：支付中
3. **PAID**：已支付
4. **COMPLETED**：已完成
5. **REFUNDING**：退款中
6. **REFUNDED**：已退款
7. **CANCELLED**：已取消
8. **FAILED**：失败

### 状态转换规则
```
PENDING → PAYING → PAID → COMPLETED
         ↓         ↓
      CANCELLED  REFUNDING → REFUNDED
         ↓         ↓
      FAILED    FAILED
```

### 触发事件
- **CREATE**：创建订单 (PENDING)
- **PAY**：发起支付 (PENDING → PAYING)
- **PAY_SUCCESS**：支付成功 (PAYING → PAID)
- **PAY_FAIL**：支付失败 (PAYING → FAILED)
- **COMPLETE**：完成订单 (PAID → COMPLETED)
- **REFUND**：申请退款 (PAID/COMPLETED → REFUNDING)
- **REFUND_SUCCESS**：退款成功 (REFUNDING → REFUNDED)
- **CANCEL**：取消订单 (PENDING → CANCELLED)

### 状态流转日志
所有状态变更都会记录在 `order_state_log` 表中，包括：
- 变更时间
- 原状态
- 新状态
- 触发事件
- 备注信息

## 常见问题

### 1. API密钥加密存储
系统使用AES加密存储支付渠道的API密钥和私钥，确保敏感信息安全。

配置加密密钥：
```yaml
payment:
  encryption:
    key: your-32-character-encryption-key
```

### 2. 订单号生成规则
订单号格式：`yyyyMMddHHmmssSSS` + 5位随机数
- 长度：20位
- 全局唯一
- 时间戳 + 随机数保证唯一性

### 3. 订单超时自动取消
系统支持订单超时自动取消功能，默认超时时间可在配置文件中设置：
```yaml
payment:
  order:
    timeout: 30  # 单位：分钟
```

### 4. 支付回调处理
支付成功后，第三方支付平台会回调 `notifyUrl`，系统会：
1. 验证签名
2. 更新订单状态
3. 记录交易流水
4. 返回处理结果

### 5. 退款处理
退款流程：
1. 调用退款接口
2. 订单状态变更为 REFUNDING
3. 调用第三方支付平台退款接口
4. 退款成功后状态变更为 REFUNDED
5. 记录退款流水

### 6. 错误处理
所有API错误都会返回统一格式：
```json
{
  "code": 500,
  "message": "错误信息",
  "data": null
}
```

常见错误码：
- 200：成功
- 400：请求参数错误
- 404：资源不存在
- 500：服务器内部错误

### 7. 分页查询
所有列表接口都支持分页查询：
- `pageNum`：页码（从1开始）
- `pageSize`：每页数量（默认10）

响应格式：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

### 8. 性能优化建议
1. 使用Redis缓存商户和渠道配置
2. 订单查询添加索引（订单号、商户ID、状态）
3. 流水表按月分表
4. 定期归档历史数据

### 9. 安全建议
1. 使用HTTPS协议
2. 定期更换加密密钥
3. 限制API访问频率
4. 启用SQL注入防护
5. 定期备份数据库
6. 监控异常订单和流水

### 10. 监控指标
建议监控以下指标：
- 订单创建成功率
- 支付成功率
- 平均支付时长
- 退款成功率
- API响应时间
- 系统错误率

## 技术支持

如有问题，请联系技术支持团队。
