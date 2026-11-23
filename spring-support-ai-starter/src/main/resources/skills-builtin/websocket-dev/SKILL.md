---
name: websocket-dev
icon: PlugConnected20Regular
title: WebSocket 实时通信指南
description: WebSocket 长连接工程实践：握手与鉴权、心跳与重连、消息协议设计、集群广播与背压控制。
version: 1.0.0
author: system
category: 研发效能
tags:
  - websocket
  - realtime
  - backend
---

# WebSocket 实时通信指南

## 一、连接与鉴权

- 握手阶段完成鉴权：token 放 query/首条消息/子协议头，禁止把长期凭证暴露在 URL 日志里。
- 鉴权失败立即关闭并返回明确 close code（如 4401），不要留半开连接。
- Origin 校验必须做，防跨站 WebSocket 劫持；网关层放行 Upgrade 头。

## 二、心跳与重连

- 服务端/客户端周期 ping/pong（如 30s），空闲超时踢除死连接。
- 客户端指数退避重连 + 抖动，重连后先 resync（拉增量/快照）再续订。
- 服务端发布/滚动重启要优雅关停：先 stop accepting，再逐个 close（带 code 让客户端感知重连）。

## 三、消息协议

- 应用层消息带 `type`、`seq`、`ts`，JSON 或二进制（protobuf）按带宽与解析成本选择。
- 请求-响应式交互要有 requestId 匹配；服务端推送用独立事件类型。
- 大消息分片或改 HTTP 上传 + WS 通知，避免单帧撑爆缓冲。

## 四、会话与集群

- 连接有状态（userId、房间、订阅列表）要集中管理：本地 SessionRegistry + Redis pub/sub 或 MQ 做跨节点广播。
- 单播/房间广播/全服广播分开封装，别手写裸 send 散落各处。
- 断线期间的消息补发：按 seq 断点续传或落库后拉取。

## 五、稳定性

- 背压：发送队列有界，慢消费者限流或踢除，防止内存被慢连接拖爆。
- 单机连接数要有上限与监控（fd、内存、线程）；压测确认容量再定水位。
- 网关/Nginx 转发要调大 proxy_read_timeout 并支持 upgrade；负载均衡用 ip_hash 或 sticky。

## 六、检查清单

- [ ] 握手鉴权 + Origin 校验，close code 语义明确
- [ ] 心跳、空闲超时、指数退避重连齐备
- [ ] 消息带 type/seq/requestId，协议可演进
- [ ] 跨节点广播经集中通道，不依赖单机内存
- [ ] 发送队列有界，连接数与积压有监控
