---
name: sql-optimization
icon: Database20Regular
title: SQL 优化指南
description: 关系型数据库 SQL 优化实战：索引设计、执行计划分析、慢查询治理、分页与锁优化。
version: 1.0.0
author: system
category: 数据库
tags:
  - sql
  - mysql
  - performance
---

# SQL 优化指南

## 一、索引设计

- 联合索引遵循最左前缀：把等值条件列放前面，范围列放最后；`a=1 AND b>2 ORDER BY c` 适合 `(a,b,c)`。
- 覆盖索引避免回表：高频查询把 SELECT 列纳入索引（`(user_id, status, created_at)`）。
- 区分度低的列（性别、状态）单独建索引意义小；长字符串用前缀索引或哈希列。
- 索引不是越多越好：写多读少的表控制索引数量，避免冗余索引（已有 `(a,b)` 不必再建 `(a)`）。

## 二、执行计划分析

- 用 `EXPLAIN`（MySQL 8 可加 `ANALYZE`）确认：type 达到 range 及以上、key 命中预期索引、rows 估算合理。
- 警惕 `Using filesort` / `Using temporary`：说明排序或分组没走索引。
- `type=ALL` 全表扫、`rows` 巨大时先考虑加索引或改写 SQL，而不是加机器。

## 三、慢查询治理

- 开启慢查询日志（`long_query_time` 建议 0.5~1s），配合 `pt-query-digest` 归因。
- 常见改写：`SELECT *` 改明确列；函数包在条件列上会失效索引（`DATE(create_time)` → 范围条件）。
- `OR` 条件两侧都要有索引才可能走 index_merge，否则改 `UNION ALL`。
- 隐式类型转换毁索引：字符串列用 `WHERE code = '123'` 而不是 `= 123`。

## 四、分页与深翻页

- 浅分页 `LIMIT offset, n` 在大 offset 时退化：改游标分页 `WHERE id > last_id ORDER BY id LIMIT n`。
- 跳转深页可用延迟关联：先查主键再回表。
- 导出场景用流式游标/分批拉取，禁止一次性 `SELECT` 百万行。

## 五、事务与锁

- 事务尽量短小；先锁热资源、统一加锁顺序，避免死锁。
- `SELECT ... FOR UPDATE` 只锁需要的行；间隙锁在 RC 隔离级下更少，按业务权衡隔离级别。
- 大批量写分批提交（如每 1000 行一批），避免长事务撑大 undo、拖垮主从延迟。

## 六、大表变更

- 千万级表加字段/索引用 online DDL（`ALGORITHM=INPLACE, LOCK=NONE`）或 gh-ost/pt-osc。
- 先在影子表验证 DDL 时长与锁行为；变更窗口避开业务高峰。
- 归档冷数据（按时间分表/归档库），控制单表规模。

## 七、检查清单

- [ ] 慢 SQL 有 EXPLAIN 证据，type/key/rows 达标
- [ ] 联合索引顺序匹配查询模式，无冗余索引
- [ ] 深分页改游标，导出走分批
- [ ] 事务短小、批量写分批提交
- [ ] DDL 走 online 工具并在低峰执行
