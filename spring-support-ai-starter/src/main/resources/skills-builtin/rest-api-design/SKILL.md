---
name: rest-api-design
icon: Cloud20Regular
title: RESTful API 设计规范
description: RESTful API 设计规范与最佳实践，覆盖资源建模、URL 设计、HTTP 方法与状态码、分页、版本、鉴权与统一错误格式。
version: 1.0.0
author: system
tags:
  - rest
  - api
  - backend
---

# RESTful API 设计规范

## 一、核心原则

- 以**资源**为中心，URL 使用名词（复数）而非动词；行为由 HTTP 方法表达。
- 资源层级表达从属关系，层级不超过两级，过深时用查询参数过滤。
- 无状态：每个请求自带鉴权与上下文，服务端不依赖会话状态。

```
GET    /api/v1/users              # 用户列表
POST   /api/v1/users              # 创建用户
GET    /api/v1/users/{id}         # 用户详情
PUT    /api/v1/users/{id}         # 全量更新
PATCH  /api/v1/users/{id}         # 局部更新
DELETE /api/v1/users/{id}         # 删除用户
GET    /api/v1/users/{id}/orders  # 用户的订单
```

## 二、HTTP 方法语义

| 方法 | 语义 | 是否幂等 | 是否安全 |
|------|------|----------|----------|
| GET | 查询 | 是 | 是 |
| POST | 创建/触发动作 | 否 | 否 |
| PUT | 全量替换 | 是 | 否 |
| PATCH | 局部更新 | 视实现 | 否 |
| DELETE | 删除 | 是 | 否 |

## 三、状态码

- `200 OK` 请求成功；`201 Created` 创建成功（返回新资源）；`204 No Content` 删除成功无返回体。
- `400 Bad Request` 参数错误；`401 Unauthorized` 未认证；`403 Forbidden` 无权限；`404 Not Found` 资源不存在。
- `409 Conflict` 冲突（如重复创建）；`422 Unprocessable Entity` 业务校验失败；`429 Too Many Requests` 限流。
- `500 Internal Server Error` 服务端错误；`502/503/504` 网关与依赖问题。

## 四、查询、分页与排序

- 分页统一参数：`page`（页码，从 1 开始）与 `pageSize`，返回总数。
- 排序 `sort=field` / `sort=-field`（负号降序）；字段过滤 `fields=a,b`；搜索用 `q`。

```
GET /api/v1/orders?status=paid&page=2&pageSize=20&sort=-createdAt
```

```json
{
  "list": [],
  "page": 2,
  "pageSize": 20,
  "total": 137
}
```

## 五、版本与兼容

- 在 URL 中带版本：`/api/v1/...`，破坏性变更才升大版本。
- 新增字段保持向后兼容；废弃字段先标记 `Deprecation` 响应头，再择机移除。

## 六、鉴权与安全

- 优先使用 Bearer Token（JWT/OAuth2），通过 `Authorization` 头传递，不放在 URL。
- 按最小权限校验；敏感操作记录审计日志；接口做限流与防重放。
- 输入做校验与转义，防止注入；返回中不泄露堆栈与内部信息。

## 七、统一响应与错误格式

成功响应结构统一；错误返回机器可读的错误码与可读信息：

```json
{
  "code": "VALIDATION_FAILED",
  "message": "参数校验失败",
  "details": [
    { "field": "email", "reason": "邮箱格式不正确" }
  ],
  "traceId": "a1b2c3"
}
```

## 八、命名约定

- URL 全部小写，多词用连字符 `-`；JSON 字段统一 `camelCase`（或团队统一 snake_case，全系统一致）。
- 时间使用 ISO 8601（UTC），金额带币种并避免浮点误差，枚举值使用固定字符串。

## 九、检查清单

- [ ] URL 为名词复数，方法语义正确且幂等
- [ ] 状态码使用准确，分页/排序参数统一
- [ ] 错误格式统一且不泄露内部信息
- [ ] 鉴权走 Authorization 头，有限流
- [ ] 版本策略清晰，保持向后兼容
