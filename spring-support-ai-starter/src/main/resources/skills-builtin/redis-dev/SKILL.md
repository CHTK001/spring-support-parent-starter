---
name: redis-dev
icon: Database20Regular
title: Redis 开发指南
description: Redis 使用与优化：数据结构选型、Key 设计、缓存三大问题、分布式锁、持久化与内存治理。
version: 1.0.0
author: system
category: 数据库
tags:
  - redis
  - cache
  - backend
---

# Redis 开发指南

## 一、数据结构选型

- String：对象 JSON/序列化值、计数器（`INCR`）；Hash：对象字段级读写（`HSET`/`HGETALL`）。
- List：简单队列/最新列表；Set：去重、共同关注（`SINTER`）；ZSet：排行榜、延迟任务（score 排序）。
- HyperLogLog 估基数、Bitmap 签到/布隆、GEO 附近的人——按场景用，不要全用 String 堆。

## 二、Key 设计

- 命名 `业务:对象:id`（如 `order:detail:1001`），可读且便于 `SCAN` 归类。
- 大 Key 治理：单 value 不超过 10KB、集合元素不超过 5000 为参考线；大 list/hash 拆分或压缩。
- 禁止 `KEYS *`：线上用 `SCAN` 增量遍历；`DEL` 大 key 改 `UNLINK` 异步释放。
- 设置 TTL 是默认习惯：没有过期时间的 key 要有明确「常驻」理由。

## 三、缓存三大问题

- 穿透（查不存在的数据）：缓存空值（短 TTL）或布隆过滤器拦截。
- 击穿（热 key 过期瞬间打库）：互斥锁重建（`SETNX` 抢占）或逻辑过期 + 后台刷新。
- 雪崩（大量 key 同时失效）：TTL 加随机抖动、多级缓存、限流兜底。
- 缓存一致性：先更 DB 再删缓存（Cache-Aside）；订阅 binlog（Canal）异步删除更稳。

## 四、分布式锁

- 用 `SET key uniqueValue NX PX ttl` 一条命令加锁；释放用 Lua 校验 value 再删，防误删他人锁。
- 业务可能超过锁 TTL 时用看门狗续期（Redisson），或把锁内逻辑控制住。
- 锁只保证互斥，不保证业务幂等；消费端仍需幂等设计。

## 五、持久化与高可用

- RDB 快照恢复快、可能丢最近数据；AOF 更安全（`everysec` 折中）；生产常两者同开。
- 主从 + 哨兵做故障转移；cluster 分片解决容量与写瓶颈。
- 只读实例承载读流量，注意主从延迟对「写后立读」的影响。

## 六、内存与淘汰

- `maxmemory` + 淘汰策略：缓存场景常用 `allkeys-lru` / `volatile-lru`；计数准确场景不能随机淘汰。
- 监控内存碎片率、命中率、慢查询日志（`SLOWLOG GET`）。
- 危险命令（`FLUSHALL`、`CONFIG`）收敛权限，走运维审批。

## 七、检查清单

- [ ] key 命名规范、有 TTL 或常驻理由
- [ ] 无大 key、热 key 有本地缓存或多副本
- [ ] 穿透/击穿/雪崩有对应方案
- [ ] 分布式锁用 NX+PX+Lua 释放
- [ ] 持久化与淘汰策略符合业务语义
