---
name: sql-helper
icon: Database20Regular
title: SQL 助手
description: 编写、优化、调试 SQL 查询，支持 MySQL/PostgreSQL/SQLite，提供查询模板与性能建议。
version: 1.0.0
author: system
tags:
  - sql
  - database
  - query
---

# SQL 助手

## 常用查询模板

### 基础查询

```sql
-- 查询
SELECT column1, column2 FROM table WHERE condition ORDER BY column LIMIT 10;

-- 插入
INSERT INTO table (col1, col2) VALUES (val1, val2);

-- 更新
UPDATE table SET col1 = val1 WHERE condition;

-- 删除
DELETE FROM table WHERE condition;
```

### 聚合查询

```sql
-- 分组统计
SELECT category, COUNT(*), AVG(price) FROM products GROUP BY category;

-- 连接查询
SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id;

-- 子查询
SELECT name FROM products WHERE price > (SELECT AVG(price) FROM products);
```

## 性能优化建议

- 避免 `SELECT *`，只查需要的列
- WHERE 条件字段加索引
- 大表分页用 `LIMIT offset, size` 时 offset 尽量小
- 避免在索引列上用函数
- EXPLAIN 查看执行计划

## 调试技巧

- 先小范围查询验证：`WHERE id = xxx`
- 分步验证 JOIN：先查单表，再加 JOIN
- 用 `EXPLAIN ANALYZE` 看实际执行时间
