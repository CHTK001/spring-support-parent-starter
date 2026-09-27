---
name: api-testing
icon: PlugConnected20Regular
title: API 测试
description: 接口测试方法，覆盖正常/异常/边界场景，提供请求模板与断言要点。
version: 1.0.0
author: system
tags:
  - api
  - testing
  - rest
---

# API 测试

## 测试维度

### 1. 正常场景
- 所有必填参数正确
- 可选参数正确
- 边界值（最大值、最小值）

### 2. 异常场景
- 缺少必填参数
- 参数类型错误
- 参数格式错误
- 权限不足
- 资源不存在

### 3. 边界场景
- 空值
- 超长字符串
- 特殊字符
- 并发请求

## 请求模板

```http
POST /api/v1/users HTTP/1.1
Host: example.com
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "张三",
  "email": "zhangsan@example.com"
}
```

## 断言要点

- [ ] 状态码正确（200/201/400/401/403/404/500）
- [ ] 响应结构正确
- [ ] 关键字段值正确
- [ ] 错误信息清晰
- [ ] 响应时间合理

## 常用工具

- curl: 命令行测试
- Postman: 图形化测试
- pytest + requests: 自动化测试
