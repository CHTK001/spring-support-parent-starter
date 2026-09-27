---
name: latex
icon: MathSymbols20Regular
title: LaTeX 排版
description: 编写 LaTeX 文档，处理公式、表格、图表、参考文献，支持论文/报告/简历。
version: 1.0.0
author: system
tags:
  - latex
  - document
  - academic
---

# LaTeX 排版

## 基础结构

```latex
\documentclass{article}
\usepackage{ctex}  % 中文支持
\usepackage{amsmath}

\title{文档标题}
\author{作者}
\date{\today}

\begin{document}
\maketitle

\section{引言}
正文内容...

\section{方法}
公式: $E = mc^2$

\end{document}
```

## 常用公式

```latex
% 行内公式
$E = mc^2$

% 行间公式
\begin{equation}
    \int_0^\infty e^{-x^2} dx = \frac{\sqrt{\pi}}{2}
\end{equation}

% 多行公式
\begin{align}
    a &= b + c \\
    d &= e + f
\end{align}
```

## 表格

```latex
\begin{tabular}{|l|c|r|}
    \hline
    左对齐 & 居中 & 右对齐 \\
    \hline
    内容 & 内容 & 内容 \\
    \hline
\end{tabular}
```

## 常用技巧

- 中文用 `ctex` 包
- 公式编号用 `equation` 环境
- 参考文献用 `bibtex`
