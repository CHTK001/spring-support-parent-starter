---
name: java-dev
icon: Code20Regular
title: Java 开发规范
description: Java 工程开发规范与最佳实践，覆盖命名与结构、OOP、集合、异常日志、并发与现代 Java 特性。
version: 1.0.0
author: system
category: 研发效能
tags:
  - java
  - backend
  - standards
---

# Java 开发规范

## 一、命名与结构

- 类名 `PascalCase`、方法与变量 `camelCase`、常量 `UPPER_SNAKE_CASE`；包名全小写。
- 一个文件一个顶级类；方法行数建议不超过 80 行，圈复杂度过高先拆分。
- 布尔变量/方法用 `isXxx` / `hasXxx`；POJO 不要用 `is` 前缀的 boolean 字段名（与序列化框架冲突）。

## 二、面向对象

- 面向接口编程，依赖注入优于 `new` 硬编码；优先组合而非继承。
- 类成员访问级别最小化；能用 `final` 就 `final`。
- 禁止在构造器中调用可被重写的方法；equals/hashCode 必须成对重写。

## 三、集合与泛型

- 初始化指定容量：`new HashMap<>(expectedSize)`，避免反复扩容。
- 遍历中删除元素用 `Iterator.remove()` 或 `removeIf`，禁止 `for` 中直接 `list.remove`。
- 对外返回不可变视图（`List.copyOf` / `Collections.unmodifiableList`）；泛型遵循 PECS（生产者 `extends`、消费者 `super`）。

## 四、异常与日志

- 捕获具体异常，禁止空 catch；不要用异常做业务流程控制。
- 日志用占位符：`log.info("order {} paid", orderId)`，禁止字符串拼接；打印异常堆栈传 `throwable` 而非 `e.getMessage()`。
- 对外错误码与内部异常分层：底层异常在边界转换成业务异常，避免堆栈泄漏给前端。

## 五、并发

- 线程池用 `ThreadPoolExecutor` 显式创建，禁止 `Executors` 无界队列的快捷方法。
- 共享可变状态优先用并发容器（`ConcurrentHashMap`）与原子类；能用不可变对象就不要加锁。
- `CompletableFuture` 组合异步时必须指定异常分支；虚拟线程（JDK 21+）适合 IO 密集任务。

## 六、现代 Java 特性

- JDK 17+：record 做数据载体、sealed 限定继承、switch 模式匹配。
- `Optional` 用于返回值，不用于字段与参数；避免 `optional.get()` 裸调用。
- 日期时间一律 `java.time`，废弃 `Date`/`Calendar`。

## 七、检查清单

- [ ] 命名与结构符合规约，方法短小单一职责
- [ ] 无空 catch、无异常控制流、日志带上下文
- [ ] 集合初始化容量正确，并发访问用安全容器
- [ ] 线程池显式创建并有拒绝策略
- [ ] 新代码使用 record/Optional/java.time 等现代特性
