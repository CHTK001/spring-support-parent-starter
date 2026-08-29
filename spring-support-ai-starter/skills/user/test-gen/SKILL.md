# 单元测试生成技能

## 描述
根据源代码自动生成单元测试用例，支持多种测试框架。

## 触发条件
- 用户请求生成测试
- 新增代码模块时

## 功能
- 自动生成测试用例
- 支持 JUnit、TestNG、Mockito
- 测试覆盖率分析
- 边界条件测试

## 使用方式
```bash
# 为指定类生成测试
skill test-gen --class com.example.service.UserService

# 为整个包生成测试
skill test-gen --package com.example.service
```

## 输出格式
- Java 测试类
- 包含 setUp 和 tearDown
- Mock 外部依赖
