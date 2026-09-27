---
name: debugging
icon: Bug20Regular
title: 调试排错
description: 系统化调试方法，快速定位和修复 bug，提供日志分析、断点调试、二分定位技巧。
version: 1.0.0
author: system
tags:
  - debugging
  - troubleshooting
  - bugfix
---

# 调试排错

## 调试方法论

### 1. 复现问题
- 明确复现步骤
- 记录环境信息
- 最小化复现用例

### 2. 定位范围
- 缩小问题范围
- 二分法定位
- 版本回溯

### 3. 根因分析
- 不是"修复表象"，要找到根因
- 为什么会发生
- 为什么之前没发现

## 调试工具技巧

### 日志分析
```python
import logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)
logger.debug(f"变量值: {x}")
```

### 断点调试
- Python: `import pdb; pdb.set_trace()`
- 浏览器: DevTools Sources 面板
- 后端: IDE 断点 + 远程调试

### 二分定位
- Git bisect 找引入 bug 的提交
- 注释掉一半代码看问题是否消失

## 常见问题排查清单

- [ ] 环境是否一致（依赖版本、配置）
- [ ] 数据是否正确（空值、格式）
- [ ] 权限是否足够
- [ ] 网络是否通
- [ ] 日志是否有报错
- [ ] 是否是缓存问题
