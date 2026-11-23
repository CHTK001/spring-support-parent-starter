---
name: go-dev
icon: Code20Regular
title: Go 开发规范
description: Go 语言工程开发指南，覆盖项目结构、错误处理、并发、接口设计、性能剖析与工具链。
version: 1.0.0
author: system
category: 研发效能
tags:
  - golang
  - backend
  - standards
---

# Go 开发规范

## 一、工程结构

- 遵循标准布局：`cmd/`（入口）、`internal/`（私有代码）、`pkg/`（可复用库）、`api/`、`configs/`。
- 包名小写单词、与目录一致；避免 `util`、`common` 这类大杂烩包名。
- `go.mod` 管理依赖，显式声明直接依赖，定期 `go mod tidy`。

## 二、错误处理

- 错误是值：逐层返回 `error`，不要忽略 `_`；用 `fmt.Errorf("xxx: %w", err)` 保留错误链。
- 哨兵错误用 `errors.Is` 判断，自定义错误类型用 `errors.As` 提取。
- 不要 panic 处理业务错误；panic 只用于不可恢复的程序员错误。

```go
if err := svc.Pay(ctx, order); err != nil {
    return fmt.Errorf("pay order %s: %w", order.ID, err)
}
```

## 三、并发

- goroutine 必须有退出路径：通过 context 取消、channel 关闭或 errgroup 管理。
- channel 传递数据，`sync.Mutex` 保护共享状态；`sync.WaitGroup` 等待一批任务。
- 禁止在循环变量上直接起 goroutine（Go 1.22 前需 `v := v` 拷贝）。

```go
g, ctx := errgroup.WithContext(ctx)
for _, job := range jobs {
    job := job
    g.Go(func() error { return process(ctx, job) })
}
if err := g.Wait(); err != nil { ... }
```

## 四、接口设计

- 接口定义在使用方一侧，小接口（1-3 个方法）优先；「越大越抽象」是反模式。
- 返回具体类型、参数用接口；`interface{}`/`any` 只用在真正需要任意类型处。
- 实现 `String()` / `Close()` 等惯例方法时遵循语义约定。

## 五、性能

- 先写正确代码，再用 `pprof`（CPU/heap/goroutine）定位瓶颈，禁止过早优化。
- 减少 map/slice 反复扩容：`make([]T, 0, n)`；大对象用 `sync.Pool`。
- 注意 string 与 []byte 转换的拷贝成本；热路径避免不必要的装箱与反射。

## 六、工具链

- 提交前：`gofmt`（或 gofumpt）、`go vet`、`golangci-lint`、`go test ./...`。
- 表驱动测试 + `t.Run` 子测试；用 `t.Cleanup` 释放资源。
- Context 一路透传：数据库、HTTP、日志都带 ctx，超时与取消不丢失。

## 七、检查清单

- [ ] 错误都有处理或显式忽略的理由，%w 保留链路
- [ ] goroutine 生命周期可控，无泄漏
- [ ] 接口小而聚焦，定义在消费方
- [ ] gofmt/vet/lint 全绿，关键路径有测试
- [ ] 热路径经过 pprof 验证而非猜测
