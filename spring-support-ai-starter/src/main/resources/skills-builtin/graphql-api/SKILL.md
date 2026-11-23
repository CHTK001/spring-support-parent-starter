---
name: graphql-api
icon: Braces20Regular
title: GraphQL API 设计
description: GraphQL 接口设计与实现：Schema 设计、Resolver、N+1 与 DataLoader、分页、鉴权与版本策略。
version: 1.0.0
author: system
category: 研发效能
tags:
  - graphql
  - api
  - backend
---

# GraphQL API 设计

## 一、Schema 设计

- 以业务能力为中心定义 type，字段名用领域语言；`Query`（查）/`Mutation`（写）/`Subscription`（订阅）职责分明。
- 非空语义要准：`ID!`、`[String!]!` 表达「必有且元素必非空」，避免调用方到处判空。
- 输入用 `input` 类型集中校验；枚举代替魔法字符串状态。

```graphql
type Order {
  id: ID!
  status: OrderStatus!
  items: [OrderItem!]!
  totalAmount: Money!
}
```

## 二、Resolver 原则

- Resolver 保持薄：取数 + 组装，业务规则下沉到服务层。
- 每个字段一个 Resolver，别在顶层 Resolver 里一次性拼整棵树。
- 错误用 `GraphQLError` 携带 code/extensions；部分成功也返回可用数据，errors 数组说明失败字段。

## 三、N+1 与性能

- GraphQL 天然容易 N+1：列表字段的关联数据用 DataLoader 按批合并请求。
- 限制查询深度与复杂度（max depth / cost analysis），防止单查询拖垮后端。
- 高频复杂查询加响应缓存或 APQ（Automatic Persisted Queries）；热字段做批量预取。

## 四、分页

- 列表统一用 Relay Cursor 连接规范（`first/after`、`last/before` + `pageInfo`），不要裸 offset。
- cursor 编码要不可猜测且稳定排序（`base64(id)` 起步，多字段排序拼接要含冲突消解）。
- `totalCount` 按需提供——计算代价高的列表别默认返回。

## 五、安全

- 鉴权放在执行前：字段级权限用 directive 或 resolver 守卫，默认拒绝未授权字段。
- 深度/复杂度/别名滥用（一次请求重复别名绕过限流）都要设防。
- 内省（introspection）在生产环境可关闭或限权，避免暴露全量 Schema。

## 六、演进

- 用「只加不删」演进 Schema：废弃字段标 `@deprecated` 并留迁移期，观察使用量后再移除。
- 客户端按操作（query/mutation 名）注册，配合 persisted query 控制---.
- 变更 breaking 的判断交给工具（graphql-inspector），不靠人眼。

## 七、检查清单

- [ ] Schema 非空语义准确，无魔法字符串
- [ ] 关联字段有 DataLoader，复杂度有上限
- [ ] 列表用 cursor 分页
- [ ] 字段级鉴权默认拒绝，introspection 受控
- [ ] 废弃字段有迁移计划
