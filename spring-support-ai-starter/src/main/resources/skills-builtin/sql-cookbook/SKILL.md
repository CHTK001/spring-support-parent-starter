---
name: sql-cookbook
icon: Database20Regular
title: SQL 实用手册
description: 常用 SQL 场景速查：多表查询、窗口函数、行转列、递归、去重更新与大数据量处理。
version: 1.0.0
author: system
category: 数据分析
tags:
  - sql
  - database
  - cookbook
---

# SQL 实用手册

## 一、多表查询

- INNER JOIN 取交集，LEFT JOIN 保左表；判断「有没有」用 EXISTS，别 COUNT 整表。
- JOIN 前先过滤再关联（子查询/CTE 先收敛数据量），尤其大表对小表。
- 注意 NULL 语义：`NOT IN` 遇到 NULL 会让整个条件失效，改用 `NOT EXISTS`。

```sql
SELECT o.order_no, u.name
FROM orders o
JOIN users u ON u.id = o.user_id
WHERE o.status = 'PAID'
  AND o.created_at >= '2026-11-01';
```

## 二、窗口函数

- 排名：`ROW_NUMBER()`（唯一）、`RANK()`（并列跳号）、`DENSE_RANK()`（并列不跳号）。
- 分组内 TopN：`ROW_NUMBER() OVER (PARTITION BY dept ORDER BY score DESC) <= 3`。
- 环比/累计：`LAG()/LEAD()` 取上期，`SUM() OVER (ORDER BY dt)` 做累计；移动平均 `AVG() OVER (... ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)`。

## 三、行转列 / 列转行

- 行转列：`MAX(CASE WHEN k='a' THEN v END) AS a, ...` 配 GROUP BY；MySQL 8/PG 可用 `PIVOT` 类语法或 json 聚合。
- 列转行：`UNION ALL` 各列，或 PG `unnest` / MySQL `JSON_TABLE`。
- 聚合拼接：`GROUP_CONCAT(name ORDER BY id SEPARATOR ',')`（MySQL）/ `STRING_AGG`（PG）。

## 四、递归与树

- 组织架构/分类树用递归 CTE：

```sql
WITH RECURSIVE tree AS (
  SELECT id, parent_id, name, 1 AS lvl
  FROM dept WHERE parent_id IS NULL
  UNION ALL
  SELECT d.id, d.parent_id, d.name, t.lvl + 1
  FROM dept d JOIN tree t ON d.parent_id = t.id
)
SELECT * FROM tree;
```

- 防死循环：递归深度上限或环检测（路径数组/`lvl` 限制）。

## 五、去重与更新

- 保留最新一条：`ROW_NUMBER() OVER (PARTITION BY biz_key ORDER BY updated_at DESC) = 1` 删除其余。
- UPSERT：MySQL `INSERT ... ON DUPLICATE KEY UPDATE`；PG `ON CONFLICT (key) DO UPDATE`——注意并发下仍可能死锁，按 key 排序批量写。
- 批量更新别逐行；`UPDATE ... JOIN` 或 `CASE WHEN` 一次完成。

## 六、大数据量

- 抽样：`ORDER BY RAND()` 全表扫不可取；大表用主键范围随机或 `TABLESAMPLE`（PG）。
- 计数：精确 count 慢时用估算（`EXPLAIN` rows）、计数表或近似（HyperLogLog 类）。
- 导出分批：按主键游标分页；深分页见 sql-optimization。

## 七、检查清单

- [ ] JOIN 有过滤下推，EXPLAIN 无全表扫
- [ ] NOT IN 已排查 NULL
- [ ] 窗口函数分组键与排序明确
- [ ] 递归 CTE 有终止保护
- [ ] 批量写走 UPSERT/分批，不逐行
