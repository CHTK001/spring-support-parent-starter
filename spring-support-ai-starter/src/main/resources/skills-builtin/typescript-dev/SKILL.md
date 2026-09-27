---
name: typescript-dev
icon: Code20Regular
title: TypeScript 开发规范
description: TypeScript 工程开发规范与最佳实践，覆盖类型系统、接口与类型别名、泛型、联合类型、严格模式与类型收窄。
version: 1.0.0
author: system
tags:
  - typescript
  - frontend
  - types
---

# TypeScript 开发规范

## 一、严格模式

- 项目必须开启严格选项：`strict: true`（含 `noImplicitAny`、`strictNullChecks`）。
- 建议开启 `noUncheckedIndexedAccess`、`noImplicitReturns`、`noFallthroughCasesInSwitch`。
- 禁止使用 `@ts-ignore` 绕过检查；确需抑制用 `@ts-expect-error` 并注明原因。
- 尽量不使用 `any`，无法确定类型时优先 `unknown`，再做收窄。

## 二、interface 与 type

- 描述对象形状、可被实现/继承时用 **`interface`**；联合、交叉、工具类型、别名用 **`type`**。
- 对外暴露的类型集中定义在 `types/`，不要在多处重复声明同一结构。
- 字段是否可选显式标注 `?`，不要把可选与 `| undefined` 混用。

```ts
interface User {
  id: number
  name: string
  email?: string
}

type UserId = User['id']
type Status = 'active' | 'inactive' | 'banned'
```

## 三、函数类型

- 显式标注参数与返回值（尤其公共 API）；避免隐式返回 `undefined`。
- 回调参数数量按需声明，不堆砌用不到的参数。

```ts
function parseId(raw: string): number | null {
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}
```

## 四、泛型

- 泛型用于复用与类型关联，命名使用有意义的大写字母（`T`、`K`、`V` 或 `TEntity`）。
- 用约束（`extends`）限定泛型能力，避免无约束的 `any` 式泛型。
- 能被推断的类型参数不要显式重复传递。

```ts
function groupBy<T, K extends string | number>(list: T[], keyOf: (item: T) => K): Record<K, T[]> {
  return list.reduce((acc, item) => {
    const key = keyOf(item)
    ;(acc[key] ??= []).push(item)
    return acc
  }, {} as Record<K, T[]>)
}
```

## 五、联合类型与收窄

- 字面量联合用于固定取值集合，配合判别字段（discriminated union）表达状态。
- 通过类型守卫收窄：`typeof`、`instanceof`、`in`、自定义谓词，不要靠强制断言。

```ts
type Result = { ok: true; data: unknown } | { ok: false; error: string }

function handle(r: Result) {
  if (r.ok) {
    console.log(r.data) // 已收窄为成功分支
  } else {
    console.error(r.error)
  }
}
```

## 六、类型断言与工具类型

- 尽量避免 `as` 断言；确需断言时一次到位，不做双重断言（`as unknown as T`）。
- 善用内置工具类型：`Partial<T>`、`Required<T>`、`Pick<T,K>`、`Omit<T,K>`、`Record<K,V>`、`Awaited<T>`。

```ts
type UserForm = Pick<User, 'name' | 'email'>
type PartialUser = Partial<User>
```

## 七、模块与导入

- 使用 ES Module（`import/export`），类型导入用 `import type` 以利擦除与打包。
- 避免循环依赖；统一从 barrel 文件（`index.ts`）导出公共类型。

## 八、检查清单

- [ ] 开启 strict，无隐式 any
- [ ] 公共 API 有完整类型，泛型有约束
- [ ] 用类型守卫收窄而非断言
- [ ] 无 `@ts-ignore`，类型导入使用 import type
- [ ] 通过 `tsc --noEmit` 类型检查
