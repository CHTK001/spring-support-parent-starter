---
name: unit-test
icon: Beaker20Regular
title: 单元测试
description: 编写单元测试，覆盖正常/异常/边界场景，提高代码质量与可维护性。
version: 1.0.0
author: system
tags:
  - testing
  - unit
  - quality
---

# 单元测试

## 测试原则

- **AAA 模式**: Arrange（准备）→ Act（执行）→ Assert（断言）
- 一个测试只测一个行为
- 测试要独立，不依赖执行顺序
- 测试要快，不依赖外部资源

## Python 示例（pytest）

```python
# test_calculator.py
import pytest
from calculator import add

def test_add_normal():
    # Arrange
    a, b = 1, 2
    # Act
    result = add(a, b)
    # Assert
    assert result == 3

def test_add_negative():
    assert add(-1, -2) == -3

def test_add_zero():
    assert add(0, 0) == 0

@pytest.mark.parametrize("a,b,expected", [
    (1, 2, 3),
    (-1, 1, 0),
    (0, 0, 0),
])
def test_add_param(a, b, expected):
    assert add(a, b) == expected
```

## 覆盖场景

- [ ] 正常输入
- [ ] 边界值（空、零、最大值、最小值）
- [ ] 异常输入（类型错误、格式错误）
- [ ] 异常抛出
- [ ] 边界条件
