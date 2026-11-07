<!--
  ReContextMenu 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReContextMenu
    :items="items"
    title="示例标题"
    subtitle="示例文本"
  />
</template>

<script setup lang="ts">
import ReContextMenu from '@repo/ui/re-context-menu';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const items = ref<PropOf<typeof ReContextMenu, 'items'>>([{ label: '示例', icon: '示例', command: '示例', danger: false, disabled: false, visible: false, separator: false, children: [] }]);
</script>
