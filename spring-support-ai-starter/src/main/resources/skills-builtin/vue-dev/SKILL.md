---
name: vue-dev
icon: Code20Regular
title: Vue 3 开发规范
description: Vue 3 工程开发规范与最佳实践，覆盖组合式 API、组件设计、Pinia、Vue Router、响应式与性能优化。
version: 1.0.0
author: system
tags:
  - vue
  - frontend
  - framework
---

# Vue 3 开发规范

## 一、基础约定

- 统一使用 **Vue 3 + 组合式 API（`<script setup>`）**，不再新增选项式 API 组件。
- 单文件组件顺序固定：`<script setup>` → `<template>` → `<style scoped>`。
- 组件文件名使用 **PascalCase**（如 `UserCard.vue`），页面级组件可放 `views/`，可复用组件放 `components/`。
- 组合式函数（composables）以 `use` 开头命名，放入 `composables/`，如 `useUserList.ts`。

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{ userId: number }>()
const emit = defineEmits<{ loaded: [data: User] }>()

const loading = ref(false)
const displayName = computed(() => `用户-${props.userId}`)
</script>

<template>
  <section :aria-busy="loading">{{ displayName }}</section>
</template>

<style scoped></style>
```

## 二、响应式使用原则

- 基础类型用 `ref`，对象/数组用 `reactive`；不要整体解构 `reactive`（会丢失响应性），需解构用 `toRefs`。
- 只读、不被修改的派生值用 `computed`，不要在 `computed` 中产生副作用。
- 监听具体数据源用 `watch`，依赖收集后立即执行用 `watchEffect`；避免监听整个大对象导致频繁触发。
- 模板中只放简单表达式，复杂逻辑收敛到 `computed` 或方法。

```ts
// 正确：监听具体字段
watch(() => form.id, async (id) => { await loadDetail(id) })
```

## 三、组件设计

- 单一职责：一个组件只做一件事，超过 300 行优先拆分。
- Props 声明类型与默认值，必传字段不设默认；通过 emits 与父组件通信，避免直接修改 props。
- 跨层级传参用 `provide/inject`，并提供注入键与默认值；全局共享状态用 Pinia，不要滥用 provide。
- 列表渲染必须绑定稳定的 `key`，禁止用数组下标作为可变列表的 key。
- 使用 `v-if` 与 `v-for` 时不要放在同一元素上。

## 四、Pinia 状态管理

- 按业务领域拆分 store，命名使用 `useXxxStore`；优先使用 Setup 写法。
- Store 中只放共享状态，组件私有状态留在组件内。
- 持久化通过插件统一处理，不在组件里手动读写 localStorage。

```ts
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const total = computed(() => items.value.reduce((s, i) => s + i.price * i.qty, 0))
  function add(item: CartItem) { items.value.push(item) }
  return { items, total, add }
})
```

## 五、Vue Router

- 路由配置按模块拆分，使用命名路由与懒加载：`component: () => import('@/views/Home.vue')`。
- 需要登录的路由统一加 `meta.requiresAuth`，在全局前置守卫中校验。
- 页面内跳转用 `router.push`，并处理重复导航异常；动态路由参数变化时用 `watch` 重新加载数据。

## 六、性能优化

- 大列表使用虚拟滚动；重型组件用 `defineAsyncComponent` 按需加载。
- 不需要深层响应的大数据（如只读表格）用 `markRaw` / `shallowRef`。
- 合理使用 `v-once`、`v-memo` 减少重复渲染；防抖/节流处理高频输入与滚动。
- 图片懒加载，路由组件预加载，构建时做分包与 Tree-shaking。

## 七、检查清单

- [ ] 使用 `<script setup lang="ts">` 且 Props/Emits 有类型
- [ ] 没有直接修改 props，没有解构 reactive
- [ ] 列表 key 稳定，computed 无副作用
- [ ] 共享状态走 Pinia，路由懒加载
- [ ] 样式使用 `scoped`，无全局污染
