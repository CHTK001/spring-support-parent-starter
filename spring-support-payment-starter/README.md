# 支付系统模块

## 功能概述

支付系统提供完整的支付解决方案,包括:

- 商户管理
- 支付渠道配置(微信/支付宝/综合支付/钱包)
- 订单管理
- 订单状态机流转
- 交易流水记录
- API密钥加密存储

## 技术栈

- Spring Boot 3.x
- MyBatis Plus
- Redis
- Spring State Machine 4.0
- 微信支付SDK
- 支付宝SDK

## 快速开始

### 1. 数据库初始化

执行 `src/main/resources/db/schema.sql` 创建数据库表。

### 2. 配置文件

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment
    username: root
    password: password
  
  redis:
    host: localhost
    port: 6379
```

### 3. API文档

启动后访问: http://localhost:8080/swagger-ui.html

## 运行模式

`spring-support-payment-starter` 支持两种使用方式:

- 嵌入式模式: 业务服务直接引用 starter，并由当前服务承载支付接口、回调、调度
- 中心化模式: 业务服务继续引用 starter 处理创建/支付/回调等核心能力，但关闭调度与运营台，由独立支付服务统一承载运维能力

关键开关:

```yaml
plugin:
  payment:
    scheduler:
      enabled: true
    ops:
      enabled: true
```

如果当前服务不是支付中心，只想复用支付核心能力，建议关闭:

```yaml
plugin:
  payment:
    scheduler:
      enabled: false
    ops:
      enabled: false
```

中心化模式完整接入说明见:

- `H:\\workspace\\2\\spring-support-api-parent\\spring-api-support-payment-starter\\支付中心化接入说明.MD`

如果要切到 `spring-support-job-starter` 承载支付调度，可进一步配置:

```yaml
plugin:
  payment:
    scheduler:
      enabled: true
      engine: job
  job:
    enable: true
    config-table-enabled: true
    job-annotation-sync-mode: UPDATE
    table-init-mode: UPDATE
    table:
      prefix: payment
```

## 核心功能

### 商户管理

- 创建商户
- 更新商户信息
- 激活/停用商户
- 商户列表查询

### 渠道配置

- 微信支付配置
- 支付宝配置(支持沙盒)
- 综合支付配置
- 钱包配置
- API密钥AES加密存储

### 订单管理

- 创建订单
- 订单状态流转(状态机)
- 订单取消
- 订单退款
- 超时自动取消

### 状态机

订单状态流转:
- 待支付 → 支付中 → 支付成功 → 已完成
- 待支付 → 已取消
- 支付成功 → 退款中 → 已退款

## 作者

CH

## 版本

4.0.0.37
