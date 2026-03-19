# 支付系统 API 文档

## 基础信息
- Base URL: `http://localhost:8080`
- Content-Type: `application/json`
- 字符编码: `UTF-8`

## 统一响应格式

### 成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": {
    // 业务数据
  }
}
```

### 错误响应
```json
{
  "code": 500,
  "message": "错误信息",
  "data": null
}
```

### 分页响应
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

## 商户管理 API

### 1. 创建商户
- **接口**: `POST /api/merchant`
- **描述**: 创建新商户
- **请求体**:
```json
{
  "merchantName": "商户名称",
  "merchantCode": "商户编码",
  "contactName": "联系人",
  "contactPhone": "联系电话",
  "contactEmail": "联系邮箱",
  "status": 1
}
```
- **响应**: 商户对象

### 2. 更新商户
- **接口**: `PUT /api/merchant/{id}`
- **描述**: 更新商户信息
- **路径参数**: `id` - 商户ID
- **请求体**: 同创建商户
- **响应**: 商户对象

### 3. 删除商户
- **接口**: `DELETE /api/merchant/{id}`
- **描述**: 删除商户
- **路径参数**: `id` - 商户ID
- **响应**: 成功/失败消息

### 4. 查询商户
- **接口**: `GET /api/merchant/{id}`
- **描述**: 根据ID查询商户
- **路径参数**: `id` - 商户ID
- **响应**: 商户对象

### 5. 商户列表（分页）
- **接口**: `GET /api/merchant/page`
- **描述**: 分页查询商户列表
- **查询参数**:
  - `pageNum`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**: 分页数据

### 6. 激活商户
- **接口**: `PUT /api/merchant/{id}/activate`
- **描述**: 激活商户
- **路径参数**: `id` - 商户ID
- **响应**: 成功/失败消息

### 7. 停用商户
- **接口**: `PUT /api/merchant/{id}/deactivate`
- **描述**: 停用商户
- **路径参数**: `id` - 商户ID
- **响应**: 成功/失败消息

## 支付渠道配置 API

### 1. 创建渠道配置
- **接口**: `POST /api/channel`
- **描述**: 创建支付渠道配置
- **请求体**:
```json
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
- **响应**: 渠道配置对象

### 2. 更新渠道配置
- **接口**: `PUT /api/channel/{id}`
- **描述**: 更新渠道配置
- **路径参数**: `id` - 渠道ID
- **请求体**: 同创建渠道配置
- **响应**: 渠道配置对象

### 3. 查询渠道配置
- **接口**: `GET /api/channel/{id}`
- **描述**: 根据ID查询渠道配置
- **路径参数**: `id` - 渠道ID
- **响应**: 渠道配置对象

### 4. 查询商户的渠道配置
- **接口**: `GET /api/channel/merchant/{merchantId}`
- **描述**: 查询指定商户的所有渠道配置
- **路径参数**: `merchantId` - 商户ID
- **响应**: 渠道配置列表

### 5. 启用渠道
- **接口**: `PUT /api/channel/{id}/enable`
- **描述**: 启用支付渠道
- **路径参数**: `id` - 渠道ID
- **响应**: 成功/失败消息

### 6. 禁用渠道
- **接口**: `PUT /api/channel/{id}/disable`
- **描述**: 禁用支付渠道
- **路径参数**: `id` - 渠道ID
- **响应**: 成功/失败消息

## 订单管理 API

### 1. 创建订单
- **接口**: `POST /api/order`
- **描述**: 创建支付订单
- **请求体**:
```json
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
- **响应**: 订单对象

### 2. 查询订单（ID）
- **接口**: `GET /api/order/{id}`
- **描述**: 根据ID查询订单
- **路径参数**: `id` - 订单ID
- **响应**: 订单对象

### 3. 查询订单（订单号）
- **接口**: `GET /api/order/no/{orderNo}`
- **描述**: 根据订单号查询订单
- **路径参数**: `orderNo` - 订单号
- **响应**: 订单对象

### 4. 订单列表（分页）
- **接口**: `GET /api/order/page`
- **描述**: 分页查询订单列表
- **查询参数**:
  - `pageNum`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**: 分页数据

### 5. 商户订单列表
- **接口**: `GET /api/order/merchant/{merchantId}`
- **描述**: 查询指定商户的订单列表
- **路径参数**: `merchantId` - 商户ID
- **查询参数**:
  - `pageNum`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**: 分页数据

### 6. 取消订单
- **接口**: `PUT /api/order/{id}/cancel`
- **描述**: 取消订单
- **路径参数**: `id` - 订单ID
- **响应**: 成功/失败消息

### 7. 完成订单
- **接口**: `PUT /api/order/{id}/complete`
- **描述**: 完成订单
- **路径参数**: `id` - 订单ID
- **响应**: 成功/失败消息

### 8. 申请退款
- **接口**: `POST /api/order/{id}/refund`
- **描述**: 申请订单退款
- **路径参数**: `id` - 订单ID
- **请求体**:
```json
{
  "refundAmount": 50.00,
  "refundReason": "用户申请退款"
}
```
- **响应**: 退款结果

### 9. 查询退款
- **接口**: `GET /api/order/{id}/refund`
- **描述**: 查询订单退款信息
- **路径参数**: `id` - 订单ID
- **响应**: 退款信息

## 交易流水 API

### 1. 查询流水（ID）
- **接口**: `GET /api/transaction/{id}`
- **描述**: 根据ID查询交易流水
- **路径参数**: `id` - 流水ID
- **响应**: 流水对象

### 2. 查询流水（订单号）
- **接口**: `GET /api/transaction/order/{orderNo}`
- **描述**: 根据订单号查询流水列表
- **路径参数**: `orderNo` - 订单号
- **查询参数**:
  - `pageNum`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**: 分页数据

### 3. 查询流水（商户ID）
- **接口**: `GET /api/transaction/merchant/{merchantId}`
- **描述**: 根据商户ID查询流水列表
- **路径参数**: `merchantId` - 商户ID
- **查询参数**:
  - `pageNum`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**: 分页数据

### 4. 流水列表（分页）
- **接口**: `GET /api/transaction/page`
- **描述**: 分页查询流水列表
- **查询参数**:
  - `pageNum`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**: 分页数据

## 数据模型

### Merchant（商户）
```json
{
  "id": 1,
  "merchantName": "商户名称",
  "merchantCode": "商户编码",
  "contactName": "联系人",
  "contactPhone": "联系电话",
  "contactEmail": "联系邮箱",
  "status": 1,
  "createTime": "2026-03-18 12:00:00",
  "updateTime": "2026-03-18 12:00:00"
}
```

### MerchantChannel（支付渠道）
```json
{
  "id": 1,
  "merchantId": 1,
  "channelType": "WECHAT",
  "channelName": "微信支付",
  "config": "加密后的配置JSON",
  "status": 1,
  "createTime": "2026-03-18 12:00:00",
  "updateTime": "2026-03-18 12:00:00"
}
```

### PaymentOrder（支付订单）
```json
{
  "id": 1,
  "orderNo": "20260318123456789012",
  "merchantId": 1,
  "channelId": 1,
  "orderAmount": 100.00,
  "currency": "CNY",
  "orderState": "PENDING",
  "subject": "商品购买",
  "body": "购买商品详情",
  "notifyUrl": "https://your-domain.com/notify",
  "returnUrl": "https://your-domain.com/return",
  "createTime": "2026-03-18 12:00:00",
  "updateTime": "2026-03-18 12:00:00"
}
```

### TransactionRecord（交易流水）
```json
{
  "id": 1,
  "orderNo": "20260318123456789012",
  "merchantId": 1,
  "transactionType": "PAYMENT",
  "transactionAmount": 100.00,
  "transactionStatus": "SUCCESS",
  "transactionTime": "2026-03-18 12:00:00",
  "createTime": "2026-03-18 12:00:00"
}
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 商户不存在 |
| 1002 | 商户已停用 |
| 2001 | 支付渠道不存在 |
| 2002 | 支付渠道已禁用 |
| 3001 | 订单不存在 |
| 3002 | 订单状态不允许该操作 |
| 3003 | 订单金额错误 |
| 4001 | 支付失败 |
| 4002 | 退款失败 |

## 状态码说明

### 商户状态
- 0: 停用
- 1: 激活

### 渠道状态
- 0: 禁用
- 1: 启用

### 订单状态
- PENDING: 待支付
- PAYING: 支付中
- PAID: 已支付
- COMPLETED: 已完成
- REFUNDING: 退款中
- REFUNDED: 已退款
- CANCELLED: 已取消
- FAILED: 失败

### 交易类型
- PAYMENT: 支付
- REFUND: 退款

### 交易状态
- SUCCESS: 成功
- FAILED: 失败
- PROCESSING: 处理中

## 安全说明

1. **HTTPS**: 生产环境必须使用HTTPS协议
2. **签名验证**: 支付回调需要验证签名
3. **加密存储**: API密钥和私钥使用AES加密存储
4. **访问控制**: 建议添加API访问权限控制
5. **频率限制**: 建议添加API访问频率限制

## 测试环境

- 测试地址: `http://test.example.com`
- Swagger文档: `http://test.example.com/swagger-ui.html`
- 测试商户: merchant_code = "TEST001"
- 测试密钥: 联系技术支持获取

## 版本历史

- v1.0.0 (2026-03-18): 初始版本
  - 商户管理
  - 支付渠道配置
  - 订单管理
  - 交易流水
  - 订单状态机
