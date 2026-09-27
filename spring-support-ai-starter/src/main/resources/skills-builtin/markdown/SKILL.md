---
name: markdown
icon: DocumentBulletList20Regular
title: Markdown 文档
description: 编写、格式化、转换 Markdown 文档，支持标题、列表、表格、代码块、引用、链接与图片。
version: 1.0.0
author: system
tags:
  - markdown
  - documentation
  - writing
---

# Markdown 文档编写

## 基本语法

```markdown
# 一级标题
## 二级标题

**加粗** *斜体* ~~删除线~~ `代码`

- 无序列表项 1
- 无序列表项 2

1. 有序列表项 1
2. 有序列表项 2

| 列1 | 列2 |
|-----|-----|
| 内容 | 内容 |

> 引用文本

```python
# 代码块
print("Hello")
```
```

## 编写规范

- 标题层级不跳级（H1 → H2 → H3）
- 列表统一用 `-`，有序列表用 `1.`
- 表格对齐用 `|---|`
- 代码块标注语言
- 链接用 `[文字](URL)` 格式

## 常用转换

```bash
# Markdown 转 HTML
pandoc input.md -o output.html

# Markdown 转 PDF
pandoc input.md -o output.pdf --pdf-engine=xelatex
```
