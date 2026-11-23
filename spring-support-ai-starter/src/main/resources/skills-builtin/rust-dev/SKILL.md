---
name: rust-dev
icon: Braces20Regular
title: Rust 开发规范
description: Rust 工程开发指南，覆盖所有权与借用、错误处理、trait 设计、unsafe 边界与 cargo 工具链。
version: 1.0.0
author: system
category: 研发效能
tags:
  - rust
  - systems
  - standards
---

# Rust 开发规范

## 一、所有权与借用

- 优先传引用（`&T` / `&mut T`），必要时再 `clone`；克隆要有注释说明原因。
- 能用借用检查器证明安全的代码，不要绕到 `Rc<RefCell<>>` 或裸指针。
- 生命周期标注从函数签名读：输入生命周期先于输出；结构体持有引用时显式标注 `'a`。

## 二、错误处理

- 库代码用 `Result<T, E>` 返回错误，定义本 crate 的错误枚举并实现 `std::error::Error`。
- 应用代码可用 `anyhow::Result` 简化；用 `thiserror` 派生错误类型。
- `?` 传播错误；禁止 `unwrap()`/`expect()` 出现在库路径（测试与启动期除外）。

```rust
pub fn parse_config(path: &Path) -> Result<Config, ConfigError> {
    let text = std::fs::read_to_string(path)?;
    let cfg: Config = serde_json::from_str(&text)?;
    Ok(cfg)
}
```

## 三、模式匹配与 Option

- `match` 必须穷尽所有分支；`_` 通配要谨慎，宁可显式列出。
- `Option` 用 `map`/`and_then`/`unwrap_or_default` 组合，少写 `match` 样板。
- 返回 `impl Iterator` / `impl Trait` 隐藏具体类型，保持 API 灵活。

## 四、trait 设计

- trait 方法尽量带默认实现，降低实现方负担；孤儿规则下优先给本 crate 类型实现。
- 泛型函数用 trait 约束表达能力需求；动态分发 `dyn Trait` 只在需要对象安全时使用。
- 实现 `Display`/`From`/`AsRef` 等标准 trait 遵循语义，不滥用 `Deref`。

## 五、unsafe 与 FFI

- `unsafe` 块必须注释 invariant（为什么这是安全的）；能封装到安全抽象内部，不暴露给调用方。
- FFI 边界检查空指针、生命周期与线程安全；C 字符串用 `CStr`/`CString`。
- 优先用成熟的 bindgen/安全封装 crate，不手写裸 extern。

## 六、工具链

- `cargo fmt` + `cargo clippy -- -D warnings` + `cargo test` 作为提交门禁。
- 依赖尽量精简：检查 `cargo tree`，避免拉入重复或过时 crate。
- 基准用 `criterion`；文档注释 `///` 覆盖公共 API，示例代码可被 `cargo test` 验证。

## 七、检查清单

- [ ] 无多余 clone，借用与生命周期清晰
- [ ] 错误统一走 Result，库路径无 unwrap/panic
- [ ] unsafe 有 invariant 注释且封装在安全抽象内
- [ ] clippy 无警告，公共 API 有文档与示例
- [ ] 依赖树健康，无重复功能 crate
