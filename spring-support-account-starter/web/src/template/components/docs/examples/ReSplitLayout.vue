<!--
  ReSplitLayout 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReSplitLayout
    :defaultLayout="defaultLayout"
    :initialLayout="initialLayout"
    storageKey="示例"
    :activeTab="activeTab"
    :panel="panel"
    :direction="'horizontal'"
    :node="node"
  />
</template>

<script setup lang="ts">
import ReSplitLayout from '@repo/ui/re-split-layout';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const defaultLayout = ref<PropOf<typeof ReSplitLayout, 'defaultLayout'>>({ type: 'split', direction: 'horizontal', children: [], sizes: [] });
const initialLayout = ref<PropOf<typeof ReSplitLayout, 'initialLayout'>>({ type: 'split', direction: 'horizontal', children: [], sizes: [] });
const activeTab = ref<PropOf<typeof ReSplitLayout, 'activeTab'>>({});
const panel = ref<PropOf<typeof ReSplitLayout, 'panel'>>({ type: 'panel', id: '示例', tabs: [{ id: '示例', panelType: '示例', label: '示例', closable: false }], activeTabId: '示例' });
const node = ref<PropOf<typeof ReSplitLayout, 'node'>>({ type: 'split', direction: 'horizontal', children: [], sizes: [] });
</script>
