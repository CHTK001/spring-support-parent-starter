---
name: pandas-guide
icon: DocumentTable20Regular
title: Pandas 数据处理指南
description: pandas 数据处理实战：数据读写与清洗、分组聚合、合并重塑、时间序列与性能优化。
version: 1.0.0
author: system
category: 数据分析
tags:
  - pandas
  - python
  - data
---

# Pandas 数据处理指南

## 一、读写与概览

- 读入先看：`df.head()`、`df.info()`（类型与缺失）、`df.describe(include='all')`。
- 类型在读取时指定（`dtype`、`parse_dates`、`usecols`），省内存且避免事后强转。
- 大文件用 `chunksize` 分块读，或换 `polars`/`duckdb`；只读需要的列（`usecols`）。

## 二、数据清洗

- 缺失值：先理解缺失含义（0？空？未采集？）再处理；`fillna`/`dropna`/`interpolate` 按业务选。
- 重复：`df.duplicated(subset=[...])` 按业务键判重；删除前留证据。
- 异常值：用业务规则（年龄<0）或统计方法（IQR、分位数截断）处理，处理要可追溯。
- 字符串标准化：`df['col'].str.strip().str.lower()`；日期统一 `pd.to_datetime(..., errors='coerce')`，坏数据变 NaT 再单独看。

## 三、分组与聚合

- `groupby` 后 `agg` 多函数多列一次算完；`transform` 把组统计广播回原表（组内占比、组均值填充）。
- 透视：`pivot_table(index, columns, values, aggfunc, fill_value)`；长宽表互转 `melt`/`pivot`。
- 多级索引慎用：能拍平就 `reset_index()`，方便导出与合并。

## 四、合并与重塑

- `merge` 明确 `how`（inner/left/right/outer）与 `validate`（'1:1'、'1:m'）防意外爆炸。
- `concat` 注意索引重复，必要时 `ignore_index=True`。
- 合并后立刻检查行数变化与关键列空值率。

## 五、时间序列

- 统一转成 DatetimeIndex 后做 `resample('D').sum()`、`rolling(7).mean()`、`shift` 构造滞后特征。
- 时区敏感数据先 `tz_localize`/`tz_convert`；跨时区聚合先统一到 UTC。
- 缺失日期补齐：`reindex(pd.date_range(...))` 再插值。

## 六、性能

- 向量化代替逐行 `apply`；链式赋值（chained indexing）会产生 SettingWithCopyWarning，用 `.loc` 一次定位。
- 类别型（category）列省内存提速；`query`/`eval` 在大表上更快。
- 复用中间结果，别把同一个 groupby 算三遍；超大表考虑分块或换引擎。

## 七、检查清单

- [ ] 读入即检查类型/缺失/重复
- [ ] 缺失与异常的处理有业务依据且留痕
- [ ] merge 指定 how/validate，行数符合预期
- [ ] 时间列统一时区与格式
- [ ] 无逐行循环，无链式赋值告警
