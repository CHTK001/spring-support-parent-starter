---
name: spring-boot-dev
icon: Box20Regular
title: Spring Boot 开发指南
description: Spring Boot 工程实践，覆盖分层架构、依赖注入与配置、事务、数据访问、Web 校验与运维端点。
version: 1.0.0
author: system
category: 研发效能
tags:
  - spring
  - java
  - backend
---

# Spring Boot 开发指南

## 一、分层架构

- Controller（参数校验/协议转换）→ Service（业务规则/事务边界）→ Mapper/Repository（数据访问）。
- DTO/VO/Entity 分离：实体不出 Service 层，接口响应用 VO；禁止把分页对象直接透传给前端。
- 一个业务动作一个 Service 方法；跨模块调用走接口，禁止注入别人的 Mapper。

## 二、依赖注入与配置

- 构造器注入（配合 Lombok `@RequiredArgsConstructor`），不用字段注入，便于测试。
- 配置用 `@ConfigurationProperties` 类型安全绑定，禁止散落的 `@Value` 魔法字符串。
- 条件装配用 `@ConditionalOnProperty` / `@Profile`；Mock/Stub 配置类必须加 profile 守卫，避免污染生产上下文。

## 三、事务

- `@Transactional` 标在 Service 层，注意 rollbackFor：默认只回滚 RuntimeException，受检异常要显式声明。
- 自调用会失效：同类内部调用事务方法走代理（拆类或 `AopContext`），不要直接 `this.method()`。
- 事务内不发 RPC、不做长耗时 IO；跨资源一致性优先考虑消息表/最终一致，而非分布式强事务。

## 四、数据访问

- MyBatis-Plus：更新/删除必须带条件，`updateById` 前确认主键来源可信；逻辑删除统一用 `@TableLogic`。
- JPA：N+1 用 `@EntityGraph` 或 fetch join 解决；分页用 `Pageable`。
- SQL 变更走版本化迁移脚本（Flyway/Liquibase），禁止启动时自动执行破坏性 DDL。

## 五、Web 与校验

- 入参用 DTO + `@Validated`（`@NotNull`/`@Size`/`@Pattern`），错误信息国际化。
- 统一响应封装与全局异常处理（`@RestControllerAdvice`），业务错误码与 HTTP 状态分离。
- 幂等：写接口用请求 ID/token 防重；防重校验放在事务外查、事务内再确认。

## 六、运维与可观测

- 暴露 `/actuator/health` 等端点时收紧敏感信息；健康检查不要依赖外部慢资源（避免聚合端点挂死）。
- 日志带 traceId（MDC），慢 SQL、异常率接入指标。
- 配置外部化：profile 区分环境；敏感信息走环境变量或配置中心，不入库不入 git。

## 七、检查清单

- [ ] 分层清晰，Entity 不外泄，无跨模块 Mapper 引用
- [ ] 事务边界正确，无自调用失效、无事务内 RPC
- [ ] 入参校验完整，统一异常与错误码
- [ ] 配置类型安全绑定，敏感信息未硬编码
- [ ] actuator 端点安全，健康检查不挂死
