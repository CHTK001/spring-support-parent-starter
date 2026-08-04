# spring-support-monitor-starter

> 统一监控模块：服务器与中间件监控、采集任务、指标时序、Redis 键值操作、AI 性能分析。

## 简介

`spring-support-monitor-starter` 是一个基于 Spring Boot 的自动化监控模块，提供：

- 🖥️ **资产主数据**：服务器、Redis、数据库等被监控对象
- ⏱️ **采集任务**：4 种 Provider（LOCAL/TERMINAL/HTTP/PROMETHEUS）
- 📊 **指标时序**：通用时序 + 5 张结构化明细子表
- 🗄️ **小时聚合**：自动按小时聚合，保留更久
- 🔑 **Redis 操作**：key 增删查改、TTL 管理
- 🤖 **AI 分析**：注入 `spring-support-ai-starter` 的 ChatClient，进行性能诊断

## 依赖

| 依赖 | 说明 |
|---|---|
| `spring-support-cache-starter` | 缓存键生成器 (`tenantedKeyGenerator`) |
| `spring-support-mybatis-starter` | BaseMapper + SysBase 基类 |
| `spring-support-datasource-starter` | JDBC 数据源 |
| `spring-support-swagger-starter` | OpenAPI 3 + knife4j |
| `spring-support-ai-starter` | ChatClient (AI 分析) |
| `utils-support-common-starter` | Trigger / Cron |
| `utils-support-oshi-starter` | 本地 OS 指标 |
| `utils-support-ssh-starter` | 远程 SSH 终端 |
| `utils-support-redis-starter` | Lettuce Redis 客户端 |

## 表清单（14 张）

| # | 表名 | 用途 |
|---|---|---|
| 1 | `ms_monitor` | 资产主表 |
| 2 | `ms_monitor_config` | 全局配置 |
| 3 | `ms_monitor_task` | 采集任务 |
| 4 | `ms_monitor_metric_config` | 解析规则 |
| 5 | `ms_monitor_gateway` | Guacamole 网关 |
| 6 | `ms_monitor_metric` | 通用时序 |
| 7 | `ms_monitor_metric_statistics` | 小时聚合 |
| 8 | `ms_monitor_metric_cpu` | CPU 明细 |
| 9 | `ms_monitor_metric_memory` | 内存明细 |
| 10 | `ms_monitor_metric_disk` | 磁盘明细 |
| 11 | `ms_monitor_metric_network` | 网络明细 |
| 12 | `ms_monitor_metric_jvm` | JVM 明细 |
| 13 | `ms_monitor_redis_metric` | Redis 时序 |
| 14 | `ms_monitor_redis_metric_statistics` | Redis 小时聚合 |

自动建表：SQL DDL 位于 `src/main/resources/db/init/V1.0__init_monitor.sql`。

## 缓存

| Cache 名 | Key 前缀 | 表 |
|---|---|---|
| `monitor` | `monitor:` | `ms_monitor` |
| `monitorTask` | `monitor.task:` | `ms_monitor_task` |
| `monitorMetricConfig` | `monitor.metricConfig:` | `ms_monitor_metric_config` |
| `monitorConfig` | `monitor.config:` | `ms_monitor_config` |
| `monitorGateway` | `monitor.gateway:` | `ms_monitor_gateway` |

时序表（`*_metric` / `*_statistics` / `*_cpu` 等）**不缓存**。

## 采集 Provider

| Provider | Service Type | 适用 |
|---|---|---|
| `LOCAL` | SERVER | oshi 直采本机（macOS 跳过） |
| `TERMINAL` | SERVER / REDIS / DB | SSH 执行自定义命令 |
| `HTTP` | SERVER | 拉取 actuator/exporter 接口 |
| `PROMETHEUS` | SERVER / REDIS / DB / MQ | 统一 Prometheus |

通过双键 `(ServiceType, ProviderType)` 在 `ProviderRegistry` 中路由。

## 接口

见 [`任务计划.md`](./任务计划.md) 末节。

## 架构

详见 [`架构图.md`](./架构图.md)。

## 任务进度

详见 [`任务计划.md`](./任务计划.md)。

## 版本

- 当前版本: `4.0.0.42`
- 父模块: `spring-support-parent-starter`
- 作者: CH
