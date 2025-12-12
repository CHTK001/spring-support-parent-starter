# Spring Support Job Starter

定时任务调度模块 - 基于数据库驱动的任务调度框架

## 功能特性

- **注解驱动**：使用 `@Job` 注解标记任务方法，自动扫描注册
- **数据库驱动**：定时查询数据库中的任务配置，触发本地任务执行
- **多种执行模式**：支持 Bean 模式、Groovy 脚本、Shell/Python 等脚本执行
- **任务日志**：完整的任务执行日志记录
- **线程池管理**：快速/慢速双线程池，避免慢任务阻塞
- **失效策略**：支持 DO_NOTHING 和 FIRE_ONCE_NOW 两种失效策略
- **时间环调度**：基于秒级时间环的精准调度

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-job-starter</artifactId>
    <version>${version}</version>
</dependency>
```

### 2. 初始化数据库

执行 `src/main/resources/sql/monitor_job.sql` 初始化任务表。

### 3. 配置属性

```yaml
plugin:
  job:
    enable: true
    pool-size: 10
    log-path: /data/applogs/job/jobhandler
    log-retention-days: 30
    trigger-pool-fast-max: 200
    trigger-pool-slow-max: 100
```

### 3. 定义任务

```java
@Component
public class DemoJobHandler {

    @Job("demoJob")
    public void execute() {
        // 任务逻辑
        System.out.println("执行任务: " + LocalDateTime.now());
    }

    @Job(value = "demoJobWithInit", init = "init", destroy = "destroy")
    public void executeWithLifecycle() {
        // 带生命周期方法的任务
    }

    public void init() {
        System.out.println("任务初始化");
    }

    public void destroy() {
        System.out.println("任务销毁");
    }
}
```

## 核心组件

| 组件 | 说明 |
|------|------|
| `JobHandler` | 任务处理器接口 |
| `JobHandlerFactory` | 任务处理器工厂，管理所有注册的处理器 |
| `BeanJobHandler` | Bean 方法任务处理器 |
| `GlueJobHandler` | Groovy 脚本任务处理器 |
| `ScriptJobHandler` | Shell/Python 等脚本任务处理器 |
| `JobAnnotationScanner` | `@Job` 注解扫描器 |
| `JobThread` | 任务执行线程 |
| `JobContext` | 任务执行上下文 |

## 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `plugin.job.enable` | `true` | 是否启用 |
| `plugin.job.pool-size` | `10` | 线程池大小 |
| `plugin.job.log-path` | `/data/applogs/job/jobhandler` | 日志路径 |
| `plugin.job.log-retention-days` | `30` | 日志保留天数 |
| `plugin.job.trigger-pool-fast-max` | `200` | 快速触发池最大线程数 |
| `plugin.job.trigger-pool-slow-max` | `100` | 慢速触发池最大线程数 |

## 任务执行类型

### Bean 模式
直接调用 Spring Bean 中标注了 `@Job` 注解的方法

### Groovy 脚本模式
支持动态编译执行 Groovy 脚本

### 脚本模式
支持 Shell、Python、PHP、NodeJS、PowerShell 等脚本执行

## 版本历史

- **4.0.0.34**: 初始版本，从 `spring-support-report-client-starter` 提取独立模块
