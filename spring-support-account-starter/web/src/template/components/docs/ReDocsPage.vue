<!--
  ReKit 组件示例站 · 页面壳

  布局：左侧面板 + 右侧预览。
  左侧面板 Tab 切换：
  - 实时：组件列表 + 参数面板（参数面板驱动右侧实时预览）
  - 实时代代码：Vue 源文件 + 一键复制
  右侧是选中示例的实时预览。
  参数面板展示该组件已定义的全部参数与示例值，修改后右侧即时回传。
-->
<template>
  <div class="docs-page">
    <!-- 顶层 -->
    <header class="docs-header">
      <h1 class="docs-header__title">ReKit 组件示例</h1>
      <p class="docs-header__desc">所有组件、所有参数，左侧面板驱动，右侧实时预览。</p>
    </header>

    <div class="docs-body">
      <!-- 左侧面板 -->
      <aside class="docs-aside">
        <div class="docs-aside__tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="docs-aside__tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="docs-aside__panel">
          <!-- 实时 Tab：搜索 + 列表 + 参数面板 -->
          <div v-if="activeTab === 'live'" class="docs-aside__content">
            <ReInput v-model="filter" placeholder="搜索组件名称" clearable class="docs-search" />

            <div class="docs-list">
              <div
                v-for="comp in pageComponents"
                :key="comp.name"
                class="docs-item"
                :class="{ active: selected === comp.name }"
                @click="onPick(comp)"
              >
                <span class="docs-item__name">{{ comp.title }}</span>
                <span class="docs-item__tag">{{ comp.name }}</span>
              </div>
            </div>

            <!-- 参数面板：值修改后驱动右侧预览 -->
            <ReCard class="docs-props" layout="simple" v-if="selectedComponent">
              <template #header>
                <ReSectionHead
                  :title="selectedComponent.title"
                  :description="`参数面板：${Object.keys(selectedComponent.props).length} 个参数，修改后实时生效`"
                />
              </template>
              <div class="docs-props__grid">
                <div v-for="(prop, k) in selectedComponent.props" :key="k" class="docs-prop">
                  <label class="docs-prop__label">{{ k }}</label>
                  <ReSwitch
                    v-if="prop.type === 'boolean'"
                    v-model="values[k]"
                    title="参数值"
                    class="docs-prop__cell"
                  />
                  <ReInput
                    v-else-if="prop.type === 'string'"
                    v-model="values[k]"
                    size="small"
                    clearable
                    class="docs-prop__cell"
                  />
                  <ReInputNumber
                    v-else-if="prop.type === 'number'"
                    v-model="values[k]"
                    size="small"
                    :controls="false"
                    class="docs-prop__cell"
                  />
                  <ReSelect
                    v-else-if="prop.options && prop.options.length"
                    v-model="values[k]"
                    :options="prop.options?.map((o) => ({ label: o, value: o }))"
                    size="small"
                    class="docs-prop__cell"
                  />
                  <span v-else class="docs-prop__other">{{ prop.example }}</span>
                </div>
              </div>
            </ReCard>
          </div>

          <!-- 实时代代码 Tab：源文件 + 一键复制 -->
          <div v-else class="docs-aside__content">
            <ReCard v-if="selectedComponent" layout="simple">
              <template #header>
                <ReSectionHead
                  :title="selectedComponent.title + ' 示例代码'"
                  :description="`examples/${selectedComponent.name}.vue`"
                />
              </template>
              <div class="docs-code">
                <pre class="docs-code__pre"><code>{{ selectedComponent.code }}</code></pre>
                <div class="docs-code__bar">
                  <ReButton size="small" :type="copied ? 'success' : 'primary'" @click="onCopyCode">
                    {{ copied ? '已复制' : '复制代码' }}
                  </ReButton>
                </div>
              </div>
            </ReCard>
          </div>
        </div>
      </aside>

      <!-- 右侧预览 -->
      <section class="docs-right">
        <div class="docs-preview">
          <div v-if="selectedComponent" class="docs-preview-inner">
            <component :is="previewComp" v-bind="previewProps" />
          </div>
          <div v-else class="docs-preview--empty">
            <ReEmpty description="选择一个组件，查看参数与实时预览" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive, shallowRef } from "vue";
import type { Component } from "vue";
import ReButton from "@repo/ui/re-button";
import ReInput from "@repo/ui/re-input";
import ReSelect from "@repo/ui/re-select";
import ReCard from "@repo/ui/re-card";
import ReSectionHead from "@repo/ui/re-section-head";
import ReInputNumber from "@repo/ui/re-input-number";
import ReEmpty from "@repo/ui/re-empty";
import ReSwitch from "@repo/ui/re-switch";
import { DOC_COMPONENTS } from "./registry";
import type { DocProp, DocComponent } from "./registry";

const tabs = [
  { key: "live", label: "实时" },
  { key: "code", label: "实时代代码" },
];

const filter = ref("");
const activeTab = ref("live");
const selected = ref("");

const pageComponents = computed(() =>
  DOC_COMPONENTS.filter((c) => c.title.includes(filter.value) || c.name.includes(filter.value))
);

const selectedComponent = computed(() => DOC_COMPONENTS.find((x) => x.name === selected.value));

/** 参数面板的值：注册表里的示例值，用户在面板中修改后右侧预览即时回传 */
const values = reactive<Record<string, any>>({});

const previewProps = computed(() => {
  const props: Record<string, any> = {};
  for (const [k, v] of Object.entries(values)) {
    if (v !== undefined) props[k] = v;
  }
  return props;
});

/** 右侧预览的懒加载示例组件 */
const previewComp = shallowRef<Component | null>(null);
const copied = ref(false);

watch(
  selected,
  async (name) => {
    previewComp.value = null;
    activeTab.value = "live";
    // 重置参数
    for (const key of Object.keys(values)) delete values[key];
    if (!name) return;
    const comp = DOC_COMPONENTS.find((x) => x.name === name);
    if (!comp) return;
    for (const [name, prop] of Object.entries(comp.props)) {
      const v = resolveExampleValue(prop);
      if (v !== undefined) values[name] = v;
    }
    // 本地懒加载示例文件，避免 slate 地把所有 88 个示例打入主包
    const mod: any = await comp.example();
    // vite 动态 import 返回模块命名空间，.default 才是组件本身
    previewComp.value = mod?.default ?? mod;
  },
  { immediate: true }
);

/** 把注册表示例字面量解析为 JS 值（string / number / boolean / array / object） */
function resolveExampleValue(prop: DocProp) {
  const example = prop.example;
  if (example === null || example === undefined || example === "") return undefined;
  const v = String(example).trim();
  if (v === "true") return true;
  if (v === "false") return false;
  if (/^-?\d+(\.\d+)?$/.test(v)) return Number(v);
  if (/^(["'])([\s\S]*)\1$/.test(v)) return v.slice(1, -1);
  if (/^\[|\{/.test(v)) {
    try {
      return JSON.parse(v);
    } catch {
      // 不是合法 JSON（未声明 key / 函数）：交回示例代码自己处理
      return undefined;
    }
  }
  return v;
}

function onPick(comp: DocComponent) {
  selected.value = comp.name;
}

function onCopyCode() {
  const code = selectedComponent.value?.code ?? "";
  navigator.clipboard.writeText(code).catch(() => {});
  copied.value = true;
  setTimeout(() => {
    copied.value = false;
  }, 1500);
}
</script>

<style scoped>
.docs-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
}

.docs-header {
  padding: 20px 28px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.docs-header__title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.docs-header__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.docs-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.docs-aside {
  width: 380px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page);
}

.docs-aside__tabs {
  display: flex;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.docs-aside__tab {
  flex: 1;
  padding: 10px 0;
  text-align: center;
  background: transparent;
  border: none;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: color .15s;
}
.docs-aside__tab.active {
  color: var(--el-text-color-primary);
  border-bottom: 2px solid var(--el-color-primary);
}

.docs-aside__panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.docs-aside__content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.docs-search {
  padding: 0 4px 8px;
}

.docs-list {
  display: flex;
  flex-direction: column;
  row-gap: 4px;
}
.docs-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background .15s;
}
.docs-item:hover {
  background: var(--el-fill-color-light);
}
.docs-item.active {
  background: var(--el-color-primary-light-9);
}
.docs-item__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.docs-item__tag {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.docs-props {
  background: var(--el-bg-color-page);
  border-radius: 6px;
  margin-top: 8px;
}

.docs-props__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 12px 0;
}
.docs-prop {
  font-size: 12px;
}
.docs-prop__label {
  display: block;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
  font-size: 11px;
}
.docs-prop__cell {
  width: 100%;
}
.docs-prop__other {
  font-family: var(--el-font-family);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 3px;
  word-break: break-all;
  display: block;
}

.docs-code {
  position: relative;
  min-height: 300px;
}
.docs-code__pre {
  margin: 0;
  padding: 12px;
  font-family: var(--el-font-family-mono);
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  overflow-x: auto;
  white-space: pre;
  word-wrap: normal;
}
.docs-code__pre code {
  font-family: inherit;
  white-space: inherit;
}
.docs-code__bar {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 1;
}

.docs-right {
  flex: 1;
  overflow: auto;
  padding: 24px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: var(--el-color-info-light-1);
}

.docs-preview {
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  min-height: 200px;
  flex: 1;
  max-width: 800px;
}

.docs-preview--empty {
  align-items: center;
  justify-content: center;
}
</style>
