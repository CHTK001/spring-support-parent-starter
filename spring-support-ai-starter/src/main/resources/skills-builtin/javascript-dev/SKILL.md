---
name: javascript-dev
icon: Braces20Regular
title: JavaScript 开发规范
description: 现代 JavaScript（ES2020+）开发规范与最佳实践，覆盖变量声明、函数、异步、模块化、DOM 与常见陷阱。
version: 1.0.0
author: system
tags:
  - javascript
  - frontend
  - standards
---

# JavaScript 开发规范

## 一、变量声明

- 默认使用 `const`，需要重新赋值时才用 `let`；不再使用 `var`。
- 声明即赋初值，变量在最小作用域内声明；命名使用有意义的 `camelCase`。
- 比较一律使用严格相等 `===` / `!==`，避免隐式类型转换带来的意外。

```js
const MAX_RETRY = 3
let attempt = 0
```

## 二、现代语法

- 字符串拼接优先模板字符串；多返回值用解构，默认值用参数默认语法。
- 使用展开/剩余运算符（`...`）替代 `arguments` 与数组方法拼接。
- 可选链 `?.` 与空值合并 `??` 处理空值；注意 `??` 不能与 `||`、`&&` 不加括号混用。

```js
const city = user?.address?.city ?? '未知'
const [first, ...rest] = items
```

## 三、函数

- 优先箭头函数保持 `this` 绑定；简单变换用纯函数，避免副作用。
- 函数参数不超过 4 个，过多时收敛为对象参数。
- 高频事件（输入、滚动、resize）使用防抖/节流。

```js
const debounce = (fn, ms) => {
  let timer
  return (...args) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), ms)
  }
}
```

## 四、异步编程

- 使用 `async/await` 处理异步，配合 `try/catch` 捕获错误；避免过深的 Promise 嵌套。
- 需要并发时用 `Promise.all`，只要一个成功用 `Promise.any`，全部完成（含失败）用 `Promise.allSettled`。
- 始终处理 Promise 的拒绝（reject），不要留下未捕获的异常。

```js
async function loadAll() {
  try {
    const [users, posts] = await Promise.all([fetchUsers(), fetchPosts()])
    return { users, posts }
  } catch (err) {
    console.error('加载失败', err)
    throw err
  }
}
```

## 五、数组与对象

- 优先使用不可变方法：`map`、`filter`、`reduce`、`find`、`some`、`every`。
- 需要短路查找用 `find/some`，不要用 `filter(...)[0]`。
- 遍历对象用 `Object.keys/values/entries`；键值集合用 `Map/Set` 替代普通对象做去重。

```js
const activeNames = users.filter(u => u.active).map(u => u.name)
const uniqueTags = new Set(posts.flatMap(p => p.tags))
```

## 六、模块化

- 使用 ES Module（`import/export`），按职责拆分模块，避免循环依赖。
- 一个模块只导出相关能力，公共入口统一从 `index.js` 暴露。

## 七、DOM 与资源

- 批量操作 DOM 时使用 `DocumentFragment` 或离线容器，减少重排。
- 事件委托优于逐个绑定；移除监听防止内存泄漏。
- 网络请求设置超时与取消（`AbortController`），敏感数据不写进前端代码。

```js
const controller = new AbortController()
fetch(url, { signal: controller.signal })
```

## 八、检查清单

- [ ] 使用 const/let 与严格相等
- [ ] 异步使用 async/await 且处理了错误
- [ ] 使用不可变数组方法与 Map/Set
- [ ] 无 var、无深层回调嵌套
- [ ] 通过 ESLint（推荐 eslint:recommended）检查
