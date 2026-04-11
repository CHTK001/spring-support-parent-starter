# 远程代理 Docker 阻塞记录

记录时间: `2026-04-10`

目标服务器:

- 公网: `8.139.4.229`
- 局域网: `172.16.0.40`
- 实际确认: 两个地址指向同一台主机

环境:

- 操作系统: `CentOS Linux 7.9.2009`
- Docker: `20.10.0`
- 宿主机 Java: `25.0.1 LTS`

## 已做验证

1. Guacamole 官方 Docker 方案

- `guacamole/guacd`
- `guacamole/guacamole:1.6.0`
- `guacamole/guacamole:1.5.5`

结果:

- 容器一旦进入 JVM 启动阶段就失败
- 典型报错:

```text
Cannot create VM thread. Out of system resources.
```

- 即使把 JVM 压到很小:

```text
-Xms32m -Xmx64m -Xss256k -XX:ThreadStackSize=256 -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:CICompilerCount=2
```

仍然无法启动

2. 数据库镜像

- `mysql:8.0`
  - 初始化数据目录失败
- `postgres:16`
  - `initdb` 报 `popen failure: Operation not permitted`
- `postgres:13`
  - 同样报 `popen failure: Operation not permitted`

## 结论

当前测试机可以运行普通容器，也能运行宿主机 Java，但不适合直接运行官方 Docker 版 JVM 远程代理栈。

任务 `25` 当前不是“未执行”，而是“已执行到运行时层并确认被环境阻塞”。

## 建议下一步

1. 优先路线

- 升级测试机 Docker 运行时，至少摆脱 `CentOS7 + Docker 20.10.0` 这一组老环境限制

2. 备选路线

- 保留这台测试机不动
- 改为宿主机部署 `Tomcat + Guacamole WAR + 宿主机数据库`
- 前端仍然沿用当前 `guacamole` 兼容远程代理配置
