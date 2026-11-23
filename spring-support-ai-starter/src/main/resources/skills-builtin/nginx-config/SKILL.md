---
name: nginx-config
icon: Cloud20Regular
title: Nginx 配置指南
description: Nginx 反向代理与网关配置：路由与 upstream、TLS、压缩与缓存、限流、WebSocket 转发与排错。
version: 1.0.0
author: system
category: 运维部署
tags:
  - nginx
  - gateway
  - ops
---

# Nginx 配置指南

## 一、基础结构

- 一个 `server{}` 一个站点；`location` 匹配顺序：`=` 精确 > `^~` 前缀 > 正则 > 普通前缀。
- 反向代理核心三行：`proxy_pass`、`proxy_set_header Host $host`、`proxy_set_header X-Real-IP $remote_addr`（再加 X-Forwarded-For/Proto 便于后端取真实 IP）。
- upstream 池 + `keepalive` 复用连接；健康检查用 `max_fails`/`fail_timeout`（开源版被动探测）。

```nginx
upstream backend {
    server 10.0.0.11:8080 max_fails=3 fail_timeout=10s;
    server 10.0.0.12:8080;
    keepalive 64;
}
```

## 二、TLS 与安全头

- 证书全链（含中间证书）部署，TLS 1.2+，开启 HSTS、session 复用。
- 安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: SAMEORIGIN`、CSP 按需。
- 对外隐藏版本：`server_tokens off;`。

## 三、性能

- 开启 gzip（文本类资源）或 brotli；静态资源长缓存 + 文件名指纹（`expires 1y` + immutable）。
- `sendfile on; tcp_nopush on;` 提升静态文件效率；大文件下载调大 `output_buffers`。
- `worker_processes auto;`、`worker_connections` 按压测定；`keepalive_timeout` 与 LB 对齐。
- body 大小：`client_max_body_size` 与业务匹配（上传接口单独放大），避免默认 1M 坑。

## 四、限流与防护

- IP 限流：`limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;` + `burst` 排队。
- 并发连接限制 `limit_conn`；慢攻击调小 `client_body_timeout`/`send_timeout`。
- 敏感路径（/actuator、/admin）加 allow/deny 网段或额外认证。

## 五、WebSocket 与长连接

- 代理升级头：

```nginx
location /ws/ {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 300s;
}
```

- SSE 转发加 `proxy_buffering off;` 与足够长的 read timeout；负载均衡对长连接用 ip_hash/sticky。

## 六、排错

- 改配置先 `nginx -t`，再 `nginx -s reload`（平滑）。
- 502/504：后端挂/超时——看 `error.log` 与 upstream 健康状况；504 多为 read timeout 过短或后端真慢。
- 循环重定向检查 `proxy_set_header X-Forwarded-Proto`；取不到真实 IP 检查 realip 模块与信任网段。
- 日志按天切割 + 冷却归档；access log 采样高 QPS 站点。

## 七、检查清单

- [ ] upstream 有 keepalive 与失败剔除
- [ ] TLS 链完整，安全头齐全，版本号隐藏
- [ ] body 大小、超时与业务匹配
- [ ] 限流配置覆盖核心入口
- [ ] WebSocket/SSE 转发参数正确
