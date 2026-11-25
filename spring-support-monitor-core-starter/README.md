# spring-support-monitor-core-starter

## 简介

`spring-support-monitor-core-starter` 是监控双模式体系的**核心模块**：沉淀 Agent 上报协议
（心跳 / 指标 / 任务 / 状态等 record 模型）、指标点模型（`MetricPoint`）与**客户端推送引擎基座**
（自动装配、能力探测、心跳/指标调度、HTTP 通道）。

模块刻意**不依赖持久层与 Web 能力**：客户端宿主仅引入本模块，配置
`plugin.monitor.mode=client` 即可周期采集本机指标并推送到 monitor 服务端；服务端宿主与
未开启该开关的存量宿主零影响（自动装配默认不生效）。

- Maven 坐标：`com.chua:spring-support-monitor-core-starter:4.0.0.51`
- 自动装配入口：`com.chua.starter.monitor.support.client.MonitorClientAutoConfiguration`
  （注册于 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）

## 能力概览

| 能力 | 说明 |
| --- | --- |
| 客户端引擎自动装配 | 仅当 `plugin.monitor.mode=client` 时装配 `MetricPushClient`；单守护线程调度心跳与指标推送，宿主关闭时自动停止 |
| 心跳上报 | 周期 `GET {server-url}/ms/monitor/agent/heartbeat`，请求头 `X-Agent-Key` 鉴权 |
| 指标推送 | 周期 `POST {server-url}/ms/monitor/agent/metrics`，负载 `{"points":[...]}`，结构与服务端 `AgentMetricsPushVO` 一致 |
| 能力探测与静默降级 | `MonitorCapabilityDetector` 探测 `probe-path`（默认 `/ms/monitor/agent/status`）：404 / 不可达视为不支持，按重探周期静默降级；心跳/推送成功刷新正向缓存 |
| 本机指标采集 | 默认 `OshiMetricCollector`（可选依赖 oshi-core）：`cpu_usage` / `memory_usage` / `disk_usage`，口径对齐服务端本机采集，落 `ms_monitor_metric_cpu` / `_memory` / `_disk` |
| 自定义采集器 SPI | 宿主注册 `MonitorMetricCollector` Bean 即可接管采集（优先于默认 OSHI 采集器） |
| HTTP 基座 | JDK `HttpClient`，固定 `NO_PROXY` 直连（不被系统代理劫持），连接/读超时可配；响应按 `ReturnResult` 信封（`code=00000`）判定业务成功 |
| 协议模型 | `AgentHeartbeatVO` / `AgentMetricsPushVO` / `AgentStatusVO` / `AgentTaskVO` / `AgentHealthVO` / `AgentConfigVO`（record），指标点 `MetricPoint` |

## 引入方式

### 1. Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-monitor-core-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

### 2. （可选）启用本机指标采集

OSHI 为可选依赖：classpath 存在时自动启用本机采集，缺失时退化为「仅心跳 + 能力探测」，
不会向宿主传递强制依赖。

```xml
<dependency>
    <groupId>com.github.oshi</groupId>
    <artifactId>oshi-core</artifactId>
    <version>6.6.5</version>
</dependency>
```

### 3. 最小配置

```yaml
plugin:
  monitor:
    mode: client                                    # 开启客户端（agent）模式，缺省不装配
    client:
      server-url: http://monitor.example.com:8082   # monitor 服务端地址（必填）
      agent-key: your-agent-key                     # 服务端纳管时下发的探针标识
```

## 配置说明

### 配置总表

| 配置项 | 说明 | 默认值 | 回落 / 生效下限 |
| --- | --- | --- | --- |
| `plugin.monitor.mode` | 客户端引擎总开关，取值 `client` 时装配 | 未配置（不装配） | — |
| `plugin.monitor.client.server-url` | monitor 服务端地址 | 无 | 为空时引擎不启动（仅告警） |
| `plugin.monitor.client.agent-key` | 探针标识（请求头 `X-Agent-Key`） | 无 | 为空时仅能力探测，不推送心跳/指标 |
| `plugin.monitor.client.push-interval-seconds` | 指标推送间隔（秒） | `15` | 小于 `5` 按 `5` 生效 |
| `plugin.monitor.client.heartbeat-interval-seconds` | 心跳间隔（秒） | `30` | 小于 `5` 按 `5` 生效 |
| `plugin.monitor.client.probe-path` | 能力探测路径 | `/ms/monitor/agent/status` | — |
| `plugin.monitor.client.reprobe-interval-seconds` | 能力重探间隔（秒） | `300` | 小于 `5` 按 `5` 生效 |
| `plugin.monitor.client.connect-timeout-ms` | HTTP 连接超时（毫秒） | `3000` | 小于 `500` 按 `500` 生效 |
| `plugin.monitor.client.read-timeout-ms` | HTTP 读超时（毫秒） | `5000` | 小于 `500` 按 `500` 生效 |

### 逐项配置示例

#### 服务端连接

**`server-url`** — monitor 服务端基地址，能力探测与心跳/指标上报都发往该地址；容忍末尾多余
`/`。默认无，为空时客户端引擎不启动。

```yaml
plugin:
  monitor:
    client:
      server-url: http://monitor.example.com:8082
```

**`connect-timeout-ms`** — 建立连接超时（毫秒）。默认 `3000`，配置小于 `500` 时按 `500` 生效。

```yaml
plugin:
  monitor:
    client:
      connect-timeout-ms: 3000
```

**`read-timeout-ms`** — 单次请求读超时（毫秒）。默认 `5000`，配置小于 `500` 时按 `500` 生效。

```yaml
plugin:
  monitor:
    client:
      read-timeout-ms: 5000
```

#### 鉴权与能力探测

**`agent-key`** — 探针标识，对应服务端 `ms_monitor.monitor_agent_key`，经请求头
`X-Agent-Key` 携带。默认无；为空时仅保留能力探测，不推送心跳与指标。

```yaml
plugin:
  monitor:
    client:
      agent-key: your-agent-key
```

**`probe-path`** — 能力探测路径，应为 monitor 专属端点：探测返回 404 视为「不支持 agent
监控」，连接失败视为「暂不可达」，其余状态（含 401/403）视为端点存在。默认
`/ms/monitor/agent/status`。

```yaml
plugin:
  monitor:
    client:
      probe-path: /ms/monitor/agent/status
```

**`reprobe-interval-seconds`** — 能力重探间隔（秒）：判定不可用/不支持后按该周期重新探测，
成功即恢复推送；探测结论（含否定结论）在该周期内缓存，不逐轮重复探测。默认 `300`，最小 `5`。

```yaml
plugin:
  monitor:
    client:
      reprobe-interval-seconds: 300
```

#### 调度周期

**`heartbeat-interval-seconds`** — 心跳间隔（秒）。默认 `30`，配置小于 `5` 时按 `5` 生效。

```yaml
plugin:
  monitor:
    client:
      heartbeat-interval-seconds: 30
```

**`push-interval-seconds`** — 指标推送间隔（秒）。默认 `15`，配置小于 `5` 时按 `5` 生效。

```yaml
plugin:
  monitor:
    client:
      push-interval-seconds: 15
```

## 使用示例

### 完整 YAML 示例（生产参考）

```yaml
plugin:
  monitor:
    mode: client
    client:
      server-url: http://monitor.example.com:8082
      agent-key: your-agent-key
      heartbeat-interval-seconds: 30
      push-interval-seconds: 15
      probe-path: /ms/monitor/agent/status
      reprobe-interval-seconds: 300
      connect-timeout-ms: 3000
      read-timeout-ms: 5000
```

### 自定义指标采集器

实现 `MonitorMetricCollector` 并注册为 Spring Bean，即可替换默认 OSHI 采集器
（示例省略 import）：

```java
@Component
public class BizMetricCollector implements MonitorMetricCollector {

    @Override
    public List<MetricPoint> collect() {
        List<MetricPoint> points = new ArrayList<>();
        points.add(MetricPoint.builder()
                .metricName("biz_order_qps")
                .value(128.0)
                .unit("qps")
                .targetTable("ms_monitor_metric")
                .build());
        return points;
    }
}
```

说明：

- `collect()` 必须自行兜底：任何异常都不允许抛出，采集失败返回空列表即可（引擎跳过本轮，不影响心跳）。
- `targetTable` 缺省时由服务端落通用表 `ms_monitor_metric`。
- 引擎按 `push-interval-seconds` 周期调用，指标点经 `POST /ms/monitor/agent/metrics` 上报。

### 指标推送负载示例

```json
{
  "points": [
    {
      "metricName": "cpu_usage",
      "value": 35.2,
      "unit": "%",
      "tags": {"cores": "8", "model": "x86"},
      "targetTable": "ms_monitor_metric_cpu"
    }
  ]
}
```

## 注意事项

- **默认关闭**：未配置 `plugin.monitor.mode=client` 时本模块不装配任何 Bean，服务端宿主与存量宿主零影响。
- **server-url 必填**：为空时引擎不启动（仅告警），不会周期重试配置。
- **agent-key 决定上报范围**：未配置时仅保留能力探测，不推送心跳与指标；日志中探针标识脱敏输出（前 4 位 + `***`）。
- **静默降级**：目标未运行 monitor（404）或暂不可达时，心跳/推送静默停止，按 `reprobe-interval-seconds` 自动重探恢复；探测结果含 401/403 视为能力存在，鉴权问题由心跳/推送链路暴露（日志可见「心跳被拒绝 / 指标推送被拒绝」）。
- **业务成败看信封**：服务端业务失败也返回 HTTP 200，客户端按 `ReturnResult.code=00000` 判定成功，勿以 HTTP 状态码为准。
- **直连不走系统代理**：HTTP 客户端固定 `NO_PROXY`，避免系统代理劫持内网地址导致探测/上报超时。
- **OSHI 失败不致命**：oshi-core 原生库初始化失败（如缺 dll）时本机采集停用并告警一次，心跳仍继续；缺失依赖则整体退化为仅心跳。
- **调度健壮性**：单守护线程调度（线程名 `monitor-client`），单次任务异常被捕获，不会杀死调度器；宿主正常关闭时自动停止并释放线程。
- **频率下限**：心跳/推送/重探间隔最小 5 秒、超时最小 500 毫秒，配置再小也按下限生效，避免打爆服务端。
- **模块边界**：本模块不包含服务端实现（Controller / 持久层 / 告警规则等在 `spring-support-monitor-starter`）；本模块仅提供客户端引擎与协议模型。
