# 代理模块接入手册

## 1. 适用场景

- 需要把代理管理能力从网关中拆出来，单独作为 starter 装配
- 需要在业务应用里托管代理实例，并通过 HTTP 接口做实例管理
- 需要先接入一个最小测试项目验证模块是否可独立启动

## 2. 引入依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-proxy-starter</artifactId>
    <version>4.0.0.37</version>
</dependency>
```

## 3. 启用模块

```yaml
plugin:
  proxy:
    enable: true
    auto-restart-running: false
```

接入建议：

- 首次接入先保持 `auto-restart-running=false`
- 等代理实例数据、过滤器配置和端口规划准备完整后，再决定是否开启自动恢复

## 4. 初始化数据表

当前代理模块自己的源码目录下没有独立的正式 SQL 文档入口，完整表结构沿用网关模块现有脚本：

`spring-support-gateway-starter/src/main/resources/db/init/V1.0__init_gateway_proxy.sql`

这份脚本包含：

- `proxy_server`
- `proxy_server_log`
- `proxy_server_setting`
- `proxy_server_setting_item`
- `proxy_server_setting_address_rate_limit`
- `proxy_server_setting_ip_rate_limit`
- `proxy_server_setting_file_storage`
- `proxy_server_setting_service_discovery`
- `proxy_server_setting_service_discovery_mapping`
- `proxy_server_setting_preview_extension`

如果只是验证“starter 能否独立装配 + 基础接口能否返回”，可以直接复用 smoke 项目的最小 SQL：

- `spring-support-module-smoke-test/src/test/resources/db/proxy/schema.sql`
- `spring-support-module-smoke-test/src/test/resources/db/proxy/data.sql`

注意：

- smoke SQL 只覆盖分页查询与统计接口所需的最小表，不等价于生产全量表结构
- 生产接入不要直接拿 smoke SQL 代替正式初始化脚本

## 5. 简单测试项目

本仓库已经复用了现有简单 Spring Boot 项目 `spring-support-module-smoke-test` 作为代理模块验证工程，不再额外新建测试项目。

代理模块对应 profile：

- `smoke-proxy`

关键配置：

- `smoke.target.module=spring-support-proxy-starter`
- `smoke.sql.init.mode=always`
- `smoke.sql.init.schema-locations=optional:classpath:db/proxy/schema.sql`
- `smoke.sql.init.data-locations=optional:classpath:db/proxy/data.sql`
- `smoke.proxy.enable=true`
- `smoke.proxy.auto-restart-running=false`

为了避免 smoke 被无关 starter 干扰，简单测试项目里额外做了两点隔离：

- 默认关闭 `plugin.swagger.enable`
- 默认关闭 `plugin.api.encode.enable`、`plugin.api.decode.enable`、`plugin.api.uniform`

这样 smoke 只验证代理模块本身和它依赖的最小链路，不把 OpenAPI 与公共编解码增强混进来放大噪音。

## 6. 验证命令

直接执行 Maven：

```bash
mvn -f H:/workspace/2/spring-support-parent-starter/pom.xml -DskipTests=false -Dmaven.test.skip=false -Dsurefire.failIfNoSpecifiedTests=false -pl spring-support-module-smoke-test -am -P smoke-proxy test
```

或执行统一脚本：

```powershell
powershell -File H:/workspace/2/spring-support-parent-starter/scripts/test/smoke/Invoke-SpringSupportModuleSmoke.ps1 -Modules spring-support-proxy-starter
```

## 7. 本轮已验证接口

- `GET /proxy/server/page?current=1&size=10`
- `GET /proxy/server/statistics`

已验证结果：

- starter 可在简单项目里独立装配成功
- H2 最小数据可正常驱动分页查询和统计接口
- `pageFor` 查询返回的代理实例字段已显式映射，不再出现只有 `filterCount` 没有实例主字段的问题

## 8. 接入注意事项

- 代理模块启用条件是 `plugin.proxy.enable=true`
- 若数据库里残留状态为 `RUNNING` 的实例，而运行环境的端口/配置尚未准备好，不要提前开启 `auto-restart-running`
- 如果后续要验证真实代理启停、HTTPS 证书、查看器配置和服务发现联动，需要在集成测试阶段补充完整表数据和更真实的运行环境

## 9. 轻控制台接入

本轮已经把代理页从 monitor 的服务管理语义里单独领出，形成独立轻控制台：

- 静态产物目录：`spring-support-proxy-starter/src/main/resources/static/proxy-console`
- 页面源码：`vue-support-parent-starter/pages/proxy`
- monitor 壳页面：`vue-support-monitor-starter/src/views/proxy-management/index.vue`

页面能力边界：

- 负责代理实例状态查看、启停、过滤链编排、JSON 配置编辑、最近日志巡检
- 不负责页面新增端口
- 端口规划应放在 yml、部署配置或初始化数据里管理

重新生成并发布静态页：

```powershell
pnpm --dir H:\workspace\2\vue-support-parent-starter --filter vue-support-spring-pages-starter publish:proxy-console
```

默认页面接口根路径：

- Spring 独立页：`../proxy/`
- Monitor 壳页：`/monitor/api/proxy/`

本轮补充验证：

- `2026-03-27` 已重新完成 `proxy-console` 构建与发布，静态资源目录已刷新到 `spring-support-proxy-starter/src/main/resources/static/proxy-console`
- 已通过 `agent-browser` 打开本地静态页，确认页面骨架、筛选区、指标卡和编排区可以正常渲染
- 在未接后端接口的本地静态服务场景下，页面会显示通用 `请求失败: 404` 提示，不再把 HTML 404 错页整段回显到页面
