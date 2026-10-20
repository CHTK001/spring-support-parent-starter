# spring-support-hotsport-starter — APM 热点观测台

基于 `utils-support-runtime-apm` 运行时 APM 数据源，提供 **REST API + 内置 Vue 前端 + 自注入 Java Agent** 的一体化观测能力。

## 组成

| 部分 | 说明 |
|------|------|
| 后端 | `HotsportApplication`（:8086），`ApmController` 暴露 `/api/apm/*`，依赖 `spring-support-common-starter` 的 `ReturnResult` 统一响应 |
| 前端 | `apps/vue-support-hotspot-starter`（Vue3 + Element Plus + ECharts），构建产物拷入 `src/main/resources/static/`，单 jar 前后端一体 |
| Agent | 启动时经 `RuntimeBoot.attachSelf()` 自注入当前 JVM，字节码引擎 + 90 余个 APM handler 上线 |

## REST 接口

前缀 `/api/apm`，统一返回 `ReturnResult<T>`：

- `GET /stats` — 概览统计（各类型总量 + `agentActive` 标记）
- `GET /transmissions?size&source&target&protocol&errorOnly` — 传输记录（调用链路）
- `GET /dependencies?size` — 依赖拓扑边
- `GET /leaks?size&resourceType&minAgeMs` — 资源泄漏
- `GET /logs?size&level` — 日志

数据源走 `StorageManager` SPI（默认内存 `inmemory`）。**能 attach 就读实时，不能 attach 则返回空 + `agentActive=false` 降级标记，前端不报错。**

## 构建 & 运行

```bash
# 1. 编译后端并打包（含前端静态资源）
cd D:/ch/project/spring-support-parent-starter
mvn -pl spring-support-hotsport-starter package -DskipTests

# 2. 一键启动前后端
bash spring-support-hotsport-starter/run.sh start
#   后端 http://127.0.0.1:8086/   (API + 内置 SPA)
#   前端 http://127.0.0.1:8890/   (vite dev 热重载, /api/apm 代理到 8086)

# 仅跑单 jar（前后端一体）
java -jar spring-support-hotsport-starter/target/spring-support-hotsport-starter-4.0.0.42.jar
# → http://127.0.0.1:8086/
```

`run.sh` 支持 `start | stop | status | restart`。

## 配置（`application.yml`）

```yaml
server:
  port: 8086
plugin:
  hotsport:
    agent:
      enabled: true      # 关闭则不注入 Agent
      shell-port: 4567   # telnet shell 端口
    apm:
      storage:
        type: inmemory   # inmemory / sqlite / noop
```

H2 内存 DataSource 仅作 `common-starter` 异常处理器的兜底，业务不写库。

## 前端重新构建

```bash
cd D:/ch/project/vue-support-parent-starter
pnpm --filter vue-support-hotspot-starter build
# 产物 dist/ 需拷到后端 static：
cp -r apps/vue-support-hotspot-starter/dist/. \
  /d/ch/project/spring-support-parent-starter/spring-support-hotsport-starter/src/main/resources/static/
# 重新 mvn package 后端
```
