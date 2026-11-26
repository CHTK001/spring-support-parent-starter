# Spring 宿主与动态 SPI 生命周期验收

`verify-spring-bridge.ps1` 使用真实 `GenericApplicationContext`、Felix 与生产发布器，执行 15 项生命周期断言。探针不启动 Spring Boot、HTTP 或 SSH 监听，不访问业务数据库，不使用 mock。

先分别编译工作区最新 `utils-support-common-starter`、`utils-support-osgi-starter` 与本模块，再执行：

```powershell
pwsh -NoProfile -File ./src/acceptance/verify-spring-bridge.ps1
```

脚本以 `--release 21` 编译验收源码；工作区 utils 和本模块 `target/classes` 优先于 Maven 仓库旧 jar。运行需要可执行已编译模块字节码的 JDK，当前实际验证环境为 JDK25。全部生成文件写入工作区 `temp/engine-concurrency/spring-acceptance/`。

API 由宿主提供，v1/v2 分别独立打包，仅包含实现与扩展索引。安装不发布，启动后自动发布；停止和卸载摘除服务；重启替换实例，更新到 v2 后既有代理调用新版实现。异步 bundle 事件以最多 5 秒条件轮询等待落定。

成功标记为 `RESULT springBridgeTotal=15 fail=0`。这组结果只验证真实容器显式装配的发布链，不表示完整 Spring Boot 自动装配、任意插件资源释放或并发升级已完成验收。
