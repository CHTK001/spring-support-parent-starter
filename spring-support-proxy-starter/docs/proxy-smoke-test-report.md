# 代理模块烟雾测试报告

## 1. 执行信息

- 模块：`spring-support-proxy-starter`
- 执行日期：`2026-03-27`
- 简单项目：`spring-support-module-smoke-test`
- 执行命令：

```bash
mvn -f H:/workspace/2/spring-support-parent-starter/pom.xml -DskipTests=false -Dmaven.test.skip=false -Dsurefire.failIfNoSpecifiedTests=false -pl spring-support-module-smoke-test -am -P smoke-proxy test
```

- 执行结果：`BUILD SUCCESS`
- 构建完成时间：`2026-03-27 00:33:05 +08:00`

## 2. 用例清单

| 测试类                   | 总用例 | 实际执行 | 跳过 | 结果 | 覆盖点                                                     |
| ------------------------ | -----: | -------: | ---: | ---- | ---------------------------------------------------------- |
| `SmokeModuleContextTest` |      9 |        5 |    4 | 通过 | 上下文启动、健康端点、模块标识、代理分页接口、代理统计接口 |

说明：

- 4 个跳过用例属于策略模块专用断言，在 `smoke-proxy` profile 下按 `Assumptions` 正常跳过

## 3. 已验证能力

- 代理 starter 可在简单 Spring Boot 项目中独立装配成功
- H2 最小表结构和初始数据足以驱动代理模块基础查询
- `GET /proxy/server/page` 可返回已种子化的代理实例
- `GET /proxy/server/statistics` 可返回总数、运行中、已停止、错误数统计
- `plugin.proxy.auto-restart-running=false` 下，smoke 启动不会触发运行中实例恢复链路

## 4. 本轮修复点

- `SystemServerMapper.pageFor` 从 `s.*` 改为显式字段投影，修复 `proxy_server_*` 到 `SystemServer` 字段的映射缺失
- `spring-support-module-smoke-test` 新增 `smoke-proxy` profile，并注入代理模块最小 schema/data
- smoke 项目为代理验证显式增加 `plugin.proxy.enable` 和 `plugin.proxy.auto-restart-running`
- smoke 项目默认关闭 `plugin.swagger.enable` 与 `plugin.api` 编解码增强，避免无关自动配置导致上下文噪音
- 统一 smoke 脚本已加入 `spring-support-proxy-starter -> smoke-proxy` 映射

## 5. 当前未覆盖项

- 真实代理实例启动、停止、重启的运行时行为
- 设置项子模块的 CRUD 与联动效果
- HTTPS 证书加载、查看器配置、服务发现映射
- 代理日志接口和异常分支

这些应继续放到更完整的集成测试阶段验证，不建议继续塞进单一 smoke 用例。
