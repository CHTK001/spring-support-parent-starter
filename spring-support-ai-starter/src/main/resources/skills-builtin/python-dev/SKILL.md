---
name: python-dev
icon: Code20Regular
title: Python 开发规范
description: Python 工程开发规范与最佳实践，覆盖 PEP 8、类型注解、项目结构、异常处理、依赖与虚拟环境管理。
version: 1.0.0
author: system
tags:
  - python
  - backend
  - standards
---

# Python 开发规范

## 一、代码风格（PEP 8）

- 使用 4 个空格缩进，不使用 Tab；行宽建议不超过 **100** 字符。
- 命名规范：模块/变量/函数 `snake_case`，类 `PascalCase`，常量 `UPPER_SNAKE_CASE`。
- 受保护成员以单下划线开头（`_internal`），避免使用双下划线包裹的魔术名称。
- 导入顺序：标准库 → 第三方库 → 本项目，每组之间空一行；禁止 `from module import *`。
- 使用 `ruff` / `black` 统一格式，`isort` 整理导入，`flake8` 或 `pylint` 做静态检查。

```python
import os
from dataclasses import dataclass

import requests


@dataclass
class UserProfile:
    user_id: int
    name: str
```

## 二、类型注解

- 公共函数与方法必须标注参数与返回类型；现代 Python 使用内置泛型（`list[int]`、`dict[str, int]`）。
- 可为空的值用 `X | None`，可选参数给默认值；复杂结构用 `dataclass` 或 `pydantic.BaseModel`。
- 使用 `mypy` 或 `pyright` 做类型检查，逐步开启严格模式。

```python
def fetch_users(limit: int = 20) -> list[dict[str, object]]:
    ...
```

## 三、项目结构

```
project/
├── src/package_name/
│   ├── __init__.py
│   ├── core/          # 核心业务逻辑
│   ├── services/      # 服务层
│   └── utils/         # 工具函数
├── tests/
├── pyproject.toml
└── README.md
```

- 业务逻辑与 IO（网络、数据库、文件）分离，保证核心逻辑可单测。
- 一个模块聚焦一类职责，`__init__.py` 保持精简，不写副作用代码。

## 四、函数与数据

- 函数短小、职责单一，参数过多（>5）时用数据对象封装。
- 优先返回/处理不可变数据；默认参数禁止使用可变对象（`def f(x=[])` 是经典陷阱）。
- 用列表/字典/生成器推导式替代冗长循环，但不嵌套过深；大数据集用生成器惰性求值。

```python
# 错误：可变默认参数
def add_item(item, bucket=[]): ...
# 正确
def add_item(item, bucket: list | None = None) -> list:
    bucket = bucket or []
    bucket.append(item)
    return bucket
```

## 五、异常处理

- 捕获具体异常，禁止裸 `except:`；不要吞掉异常，至少记录日志。
- 资源释放用 `with`（上下文管理器）；自定义异常继承自具体的内置异常基类。
- 不在异常的 `else`/`finally` 中掩盖原始错误，必要时用 `raise ... from err` 保留因果链。

```python
try:
    resp = requests.get(url, timeout=10)
    resp.raise_for_status()
except requests.RequestException as exc:
    raise RuntimeError(f"请求失败: {url}") from exc
```

## 六、依赖与环境

- 使用虚拟环境（`venv` / `uv` / `poetry`）隔离依赖，不污染系统 Python。
- 依赖在 `pyproject.toml` / `requirements.txt` 中锁定版本，区分运行依赖与开发依赖。
- 敏感配置通过环境变量或配置中心注入，不硬编码密钥。

## 七、测试

- 使用 `pytest`，测试文件以 `test_` 开头，一个测试只验证一个行为。
- 使用 fixture 管理前置数据，用参数化覆盖多组输入；外部依赖用 mock 或替身。
- 核心模块保持高覆盖率，测试必须可重复、无相互依赖。

## 八、检查清单

- [ ] 符合 PEP 8，已通过格式化与 lint
- [ ] 公共函数有类型注解，无可变默认参数
- [ ] 捕获具体异常，资源使用 with
- [ ] 依赖已锁定，使用虚拟环境
- [ ] 关键逻辑有测试且可独立运行
