---
name: regex-helper
icon: Search20Regular
title: 正则表达式
description: 编写、调试正则表达式，匹配邮箱、手机号、URL、日期等常用模式。
version: 1.0.0
author: system
tags:
  - regex
  - pattern
  - text
---

# 正则表达式

## 常用模式

### 验证类

```python
import re

# 邮箱
email = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'

# 手机号（中国）
phone = r'^1[3-9]\d{9}$'

# URL
url = r'^https?://[^\s/$.?#].[^\s]*$'

# 日期（YYYY-MM-DD）
date = r'^\d{4}-\d{2}-\d{2}$'

# IP 地址
ip = r'^(\d{1,3}\.){3}\d{1,3}$'
```

### 提取类

```python
# 提取所有数字
re.findall(r'\d+', text)

# 提取所有邮箱
re.findall(r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}', text)

# 提取 URL
re.findall(r'https?://[^\s<>"]+', text)
```

## 常用元字符

- `.` 任意字符
- `*` 0次或多次
- `+` 1次或多次
- `?` 0次或1次
- `{n}` 恰好n次
- `{n,m}` n到m次
- `^` 开头
- `$` 结尾
- `[]` 字符集
- `()` 分组

## 调试技巧

- 用 `re.search()` 测试单个匹配
- 用 `re.findall()` 看所有匹配
- 在线工具：regex101.com
