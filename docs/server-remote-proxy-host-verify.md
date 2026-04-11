# 远程代理宿主机方案验收记录

记录时间: `2026-04-11`

## 验收范围

- 任务: `25`
- 测试服务器: `172.16.0.40` / `8.139.4.229`
- 最终采用方案: `guacd Docker + 宿主机 Tomcat + guacamole.war + guacamole-auth-json`

## 实际环境

- 操作系统: `CentOS Linux 7.9.2009`
- Docker: `20.10.0`
- Tomcat: `tomcat-7.0.76-16.el7_9.noarch`
- Guacamole 入口: `http://172.16.0.40:18088/guacamole/`
- guacd: `guacamole/guacd:1.6.0`

## 实测结果

1. `guacd` 已启动并保持运行
2. Tomcat 已启动并监听 `18088`
3. Guacamole 首页访问返回 `HTTP/1.1 200 OK`
4. JSON Auth 扩展已加载，`/etc/guacamole/extensions/guacamole-auth-json-1.6.0.jar` 存在
5. `guacamole.properties` 已配置 `json-secret-key`
6. 本地脚本已可生成 `?data=` 形式的 JSON Auth 直达地址
7. `/usr/share/tomcat/conf/server.xml` 与 `/etc/tomcat/server.xml` 已统一修正:
   - HTTP 端口: `18088`
   - AJP 端口: `8009`

## 关键命令结果

- `docker ps --format '{{.Names}}|{{.Image}}|{{.Ports}}'`
  - `guacd|guacamole/guacd:1.6.0|127.0.0.1:4822->4822/tcp`
- `ss -lntp | grep 18088`
  - Tomcat Java 进程监听 `*:18088`
- `curl -I http://127.0.0.1:18088/guacamole/`
  - 返回 `HTTP/1.1 200 OK`

## 结论

- 任务 `25` 按当前测试机可落地方案已完成。
- 当前测试机不适合继续使用“Guacamole Web 官方全 Docker 栈 + 官方数据库镜像”。
- 远程代理最终落地方案不是纯 Docker Web 容器，而是:
  - `guacd` 走 Docker
  - `Guacamole Web` 走宿主机 WAR
  - `认证` 走 JSON Auth
- 该方案不依赖 MySQL，已满足当前 `server-starter` 的远程控制接入要求。
