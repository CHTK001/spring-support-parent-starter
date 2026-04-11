# 远程代理测试环境探测记录

记录时间: `2026-04-10 20:25 +08:00`

## 目标

为任务 `25` 提前确认测试服务器是否具备远程代理 Docker 部署条件。

## 探测方式

- 使用用户提供的 `root` 账号通过 `plink` 直接连接测试服务器
- 执行:
  - `uname -a`
  - `command -v docker`
  - `docker --version`
  - `docker ps --format '{{.Names}}|{{.Image}}|{{.Status}}'`（公网机）

## 探测结果

### 1. 8.139.4.229

- SSH: `通过`
- 操作系统: `Linux iZbp1eeysy7bvhbtq00ratZ 3.10.0-1160.119.1.el7.x86_64`
- Docker: `/usr/bin/docker`
- Docker 版本: `20.10.0`
- 现有容器:
  - `smartjavaai-test | ubuntu:22.04 | Up 9 days`
  - `monitor-docker-e2e | python:3.10-slim | Up 13 days`
  - `zookeeper | confluentinc/cp-zookeeper:7.4.4 | Up 4 months`

结论:
- 已满足 Docker 部署基础条件
- 服务器上已有长期运行容器，后续部署远程代理时必须避开现有端口与容器命名

### 2. 172.16.0.40

- SSH: `通过`
- 首次连接需要显式确认主机指纹
- 返回主机信息: `Linux iZbp1eeysy7bvhbtq00ratZ 3.10.0-1160.119.1.el7.x86_64`
- Docker: `/usr/bin/docker`
- Docker 版本: `20.10.0`

结论:
- 具备 Docker 部署基础条件
- 该地址返回的主机名与 `8.139.4.229` 一致，疑似同一台机器的内外网地址，或当前测试环境存在地址映射/跳转
- 在真正执行远程代理部署前，需要先确认 `172.16.0.40` 与 `8.139.4.229` 是否为同一台服务器，避免重复部署或误判联调结果

## 当前判断

- 任务 `25` 目前从“环境不确定”推进到了“部署条件已确认”
- 真正的远程代理 Docker 落地在当前测试机上被阻塞
- 已验证:
  - 宿主机 `java -version` 正常，说明本机 Java 环境没问题
  - `CentOS 7.9 + Docker 20.10.0` 下，`guacamole/guacamole` 容器内的 JVM 无法启动，最小化到 `-Xms32m -Xmx64m -Xss256k -XX:+UseSerialGC` 仍报 `Cannot create VM thread. Out of system resources.`
  - `postgres:13/16` 官方镜像在该 Docker 运行时下初始化数据库时都会报 `popen failure: Operation not permitted`
  - 先前尝试的 `mysql:8.0` 在该环境下也出现初始化数据目录不可用问题
- 结论:
  - 当前测试机不适合继续用“官方 Docker 版 Guacamole + 官方数据库镜像”完成任务 `25`
  - 后续可行路径只剩两类:
    - 升级测试机 Docker / 宿主机运行时后继续走 Docker 方案
    - 改为宿主机部署 Tomcat + Guacamole WAR + 本机数据库，而不是 JVM 容器化
