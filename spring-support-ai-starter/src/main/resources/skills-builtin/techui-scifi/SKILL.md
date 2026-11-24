---
name: techui-scifi
icon: ChartMultiple20Regular
title: TechUI SciFi 科技风大屏
description: 用 @techui/scifi 开发科技风数据大屏：初始化、全局组件（面板/标题/翻牌/进度池）、等比适配与本项目集成要点。
version: 1.0.0
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

- `@techui/scifi` 是 TechUI 的科技风 SVG 数据可视化组件库（Vue 3），适合监控大屏、驾驶舱、展厅屏、可视化驾驶舱页面。
- 依赖两件套：`@techui/scifi` + `@techui/themes`（主题 CSS，如 `@techui/themes/static/darkBlack.css`）。
- **vite 必须配置 `optimizeDeps.exclude: ["@techui/scifi"]`**：其 wasm 核心按真实文件路径加载，预打包（pre-bundling）会破坏路径导致初始化失败。
- 库为全量包设计，不做按需 import；组件经初始化后全局注册，模板直接使用。

## 二、初始化（必须且仅一次）

```ts
import type { App } from "vue";
import TechUIInit from "@techui/scifi";
import "@techui/themes/static/darkBlack.css"; // 主题变量 + 组件样式

let ready: Promise<void> | null = null;
function ensureTechUI(app: App): Promise<void> {
  return (ready ??= TechUIInit({ app, initLang: "zh-CN" }).catch((e) => {
    ready = null; // 失败清空守卫，允许重试
    throw e;
  }));
}
```

- 在组件内用 `getCurrentInstance()?.appContext.app` 取宿主 app 传入（组件挂载晚于 `app.mount`，运行时 use 依然有效）。
- 初始化是**异步**的（加载 wasm 核心 + 注册组件），用 `loading / ready / error` 状态机包裹，失败给兜底提示。
- 可选参数：`license`、`subLicense`、`features: { echarts, advanced }`、`debug`。无 license 且未开 advanced 时，3D 与 echarts 系列组件（Tui3DPanel、TuiBar3D、TuiEcharts 等）不会注册。

## 三、组件速查（全局注册，模板直接用，无需 import）

- **上下文**：面板类组件依赖 `TuiProvider` 注入的运行时上下文，整屏内容包在 `<TuiProvider>` 内。
- **头部/标题**：`ScifiHeaderA1`（text、width）、`TuiTitleA1`（title）。
- **数字**：`TuiDigitalFlip`（value、duration，数字翻牌）、`TuiProgressPool`（label、value、max-value，进度池）。
- **标签**：`TuiTag`（type: primary/success/info/warning/danger、size、appearance="marker" 圆点态）。
- **页签**：`ScifiTabs`（v-model + options: `{label, value}[]`）。
- **按钮**：`ScifiButtonA1`（appearance: "A"|"B"|"C"、type: primary/success/warning，支持原生 click）。
- **装饰/容器**：`ScifiHexagon`（width、height）、`ScifiDecoA1`、`ScifiDecoB1`；面板系 `ScifiPanelA1`~`ScifiPanelDV9`。
- **别名机制**：A1 系列有语义别名且完全等价，如 `ScifiButtonA1` = `ScifiButtonHex`、`ScifiHeaderA1` = `ScifiHeaderSurge`、`TuiTitleA1` = `TuiTitleEave`，模板里写哪个都行。

## 四、大屏适配口径

- 按 **1920×1080 设计稿**布局；外层 `ResizeObserver` 实测舞台尺寸，`scale = min(w/1920, h/1080)`，内容层 `transform: scale(s)`、`transform-origin: center` 等比缩放，禁止拉伸变形。
- 深色底 + 高对比青蓝主色；布局层级：KPI 数值卡在上，趋势/负载居中，明细与操作在下。
- 组件接 `class` 会落到根元素，可用 `:deep()` 微调内部样式。

## 五、最小可运行骨架

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
const app = getCurrentInstance()?.appContext.app;
onMounted(async () => {
  try { await ensureTechUI(app!); state.value = "ready"; }
  catch { state.value = "error"; }
});
</script>

<template>
  <div class="screen">
    <div v-if="state === 'ready'" class="canvas">
      <TuiProvider>
        <ScifiHeaderA1 text="监控大屏" :width="1848" />
        <TuiDigitalFlip :value="12864" :duration="1500" />
        <TuiProgressPool label="CPU 负载" :value="72" :max-value="100" />
        <TuiTag type="success" size="small">+4.2%</TuiTag>
      </TuiProvider>
    </div>
    <span v-else-if="state === 'error'">初始化失败</span>
    <span v-else>加载中…</span>
  </div>
</template>
```

## 六、本项目参考实现

- `pages/screen/src/scifi.ts`：大屏展厅注册表 `component` 工厂 + 懒加载入口（id=scifi-demo）。
- `pages/screen/src/screens/ScifiDemoScreen.vue`：完整示例——初始化单例、状态机、ResizeObserver 等比缩放、KPI/告警/按钮/装饰组件用法。
- `apps/vue-support-monitor-starter/vite.config.ts`：`optimizeDeps.exclude` 参考配置。

## 七、检查清单

- [ ] `TechUIInit` 全局只执行一次，且传入宿主 app
- [ ] 面板类组件包在 `TuiProvider` 内
- [ ] vite `optimizeDeps.exclude` 含 `@techui/scifi`
- [ ] 1920×1080 设计稿 + ResizeObserver 等比缩放
- [ ] 主题 CSS（如 darkBlack.css）已随页面引入
- [ ] 初始化失败有 loading/error 兜底，不白屏
