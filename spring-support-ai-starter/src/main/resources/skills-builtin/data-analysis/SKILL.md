---
name: data-analysis
icon: ChartMultiple20Regular
title: 数据分析
description: 使用 Python（pandas/numpy/matplotlib）进行数据清洗、统计分析、可视化与报告生成。
version: 1.0.0
author: system
tags:
  - data
  - analysis
  - visualization
---

# 数据分析

## 环境

```bash
python -m pip install pandas numpy matplotlib seaborn
```

## 数据读取

```python
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

# 读取数据
df = pd.read_csv('data.csv')
df = pd.read_excel('data.xlsx')

# 快速查看
df.head()
df.info()
df.describe()
```

## 数据清洗

```python
# 处理缺失值
df.dropna()           # 删除缺失
df.fillna(0)          # 填充

# 去重
df.drop_duplicates()

# 类型转换
df['date'] = pd.to_datetime(df['date'])
```

## 统计分析

```python
# 分组聚合
df.groupby('category')['value'].agg(['mean', 'sum', 'count'])

# 相关性
df.corr()

# 透视表
pd.pivot_table(df, values='sales', index='month', columns='product', aggfunc='sum')
```

## 可视化

```python
# 折线图
plt.plot(df['date'], df['value'])
plt.title('趋势图')
plt.savefig('trend.png')

# 柱状图
sns.barplot(x='category', y='value', data=df)
plt.savefig('bar.png')
```
