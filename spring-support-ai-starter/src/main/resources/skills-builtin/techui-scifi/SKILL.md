---
name: techui-scifi
icon: ChartMultiple20Regular
title: TechUI SciFi 科技风大屏
description: 用 @techui/scifi 开发科技风数据大屏：初始化、TuiProvider、全局组件（面板/标题/翻牌/进度池）、等比适配与集成要点。
version: 1.1.0
author: system
category: 开发效率
tags:
  - visualization
  - dashboard
  - vue3
  - techui
---

# TechUI SciFi 科技风大屏

## 一、定位与依赖

- `@techui/scifi`：TechUI 科技风 SVG/FUI 数据可视化组件库（Vue 3），适合监控大屏、驾驶舱、展厅屏。
- 生态依赖：`@techui/colors/mixins/locales/themes/libs/utils`。**pnpm 安装时可能不会自动装齐**，报模块缺失就手动补装。
- 主题 CSS 来自 `@techui/themes`（如 `@techui/themes/static/darkBlack.css`）。
- **vite 必须 `optimizeDeps.exclude: ["@techui/scifi", "@techui/themes"]`**：库用 wasm 提供计算/许可/模板能力，预构建会破坏 wasm 加载；Vite 7 + TS 下 `@techui/themes` 不排除也会报错，调不平就把生态包全部加入 exclude 排查。
- 全量包设计，不做按需 import；组件初始化后全局注册，模板直接用。

## 二、初始化（必须且仅一次）

```ts
import type { App } from "vue";
import TechUIInit from "@techui/scifi";
import "@techui/themes/static/darkBlack.css";

let ready: Promise<void> | null = null;
function ensureTechUI(app: App): Promise<void> {
  return (ready ??= TechUIInit({ app, initLang: "zh-CN" }).catch((e) => {
    ready = null; // 失败清空守卫，允许重试
    throw e;
  }));
}
```

- 在组件内用 `getCurrentInstance()?.appContext.app` 取宿主 app 传入。
- 初始化是**异步**的（加载 wasm + 注册组件），用 `loading/ready/error` 状态机包裹，失败给兜底。
- 参数：`license`、`subLicense`、`features: { echarts, advanced }`、`debug`。3D/Echarts 组件需开对应 feature（`advanced` 未授权也可体验，默认关闭）。

## 三、TuiProvider 与嵌入定位（易踩坑）

- 面板类组件依赖 `TuiProvider` 注入的运行时上下文，整屏内容包在 `<TuiProvider>` 内。
- `TuiProvider` 根容器 `.tui-root` **默认 `position: fixed; inset: 0` 铺满视口**。嵌入页面局部（如展厅卡位）时，父容器必须有定位和尺寸，并传 `:root-style="{ position: 'absolute', inset: 0 }"`，否则会盖住整个页面。

## 四、组件速查（全局注册，模板直接用）

- **头部/标题**：`ScifiHeaderA1`（text、width）、`TuiTitleA1`（title）。
- **数字**：`TuiDigitalFlip`（value、duration 翻牌）、`TuiProgressPool`（label、value、max-value 进度池）。
- **标签/页签**：`TuiTag`（type: primary/success/info/warning/danger、appearance="marker" 圆点）、`ScifiTabs`（v-model + options）。
- **按钮**：`ScifiButtonA1`~`A5`（appearance: "A"|"B"|"C"、type、原生 click）。
- **装饰/容器**：`ScifiHexagon`、`ScifiDecoA1/B1`；面板系 `ScifiPanelA1`~`ScifiPanelDV9`。
- **别名**：A1 系列有等价语义别名，如 `ScifiButtonA1`=`ScifiButtonHex`、`TuiTitleA1`=`TuiTitleEave`，写哪个都行。

## 五、大屏适配口径

- 按 **1920×1080 设计稿**布局；外层 `ResizeObserver` 实测舞台尺寸，`scale = min(w/1920, h/1080)`，内容层 `transform: scale(s)`、`transform-origin: center` 等比缩放，禁止拉伸。
- 深色底 + 高对比青蓝主色；KPI 在上、趋势居中、明细在下。
- 组件接 `class` 落根元素，可用 `:deep()` 微调内部样式。

## 六、最小骨架

```vue
<script lang="ts">
import type { App } from "vue";
import TechUIInit from "@techui/scifi";
import "@techui/themes/static/darkBlack.css";
let ready: Promise<void> | null = null;
const ensureTechUI = (app: App) =>
  (ready ??= TechUIInit({ app, initLang: "zh-CN" }).catch((e) => { ready = null; throw e; }));
</script>
<script setup lang="ts">
import { getCurrentInstance, onMounted, ref } from "vue";
const state = ref<"loading" | "ready" | "error">("loading");
onMounted(async () => {
  try { await ensureTechUI(getCurrentInstance()!.appContext.app!); state.value = "ready"; }
  catch { state.value = "error"; }
});
</script>
<template>
  <div class="screen">
    <TuiProvider v-if="state === 'ready'" :root-style="{ position: 'absolute', inset: 0 }">
      <ScifiHeaderA1 text="监控大屏" :width="1848" />
      <TuiDigitalFlip :value="12864" :duration="1500" />
      <TuiProgressPool label="CPU 负载" :value="72" :max-value="100" />
    </TuiProvider>
    <span v-else-if="state === 'error'">初始化失败</span>
    <span v-else>加载中…</span>
  </div>
</template>
```

## 七、本项目参考实现

- `pages/screen/src/scifi.ts`：展厅注册表 component 工厂 + 懒加载入口（id=scifi-demo）。
- `pages/screen/src/screens/ScifiDemoScreen.vue`：完整示例（单例初始化、状态机、ResizeObserver 缩放、组件用法）。
- `apps/vue-support-monitor-starter/vite.config.ts`：optimizeDeps.exclude 参考。

## 八、检查清单

- [ ] `TechUIInit` 全局只执行一次，且传入宿主 app
- [ ] 内容包在 `TuiProvider` 内；嵌入局部时父容器有定位尺寸 + `root-style` 改 absolute
- [ ] vite `optimizeDeps.exclude` 含 `@techui/scifi`（Vite 7+TS 再加 `@techui/themes`）
- [ ] pnpm 环境生态包安装齐全
- [ ] 1920×1080 设计稿 + ResizeObserver 等比缩放
- [ ] 主题 CSS 已随页面引入；初始化失败有兜底不白屏
