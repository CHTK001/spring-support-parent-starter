<!--
  ReTabs 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTabs
    :tabs="tabs"
    modelValue="示例"
    :closable="false"
    :contextMenu="contextMenu"
    :showCloseAll="false"
    emptyText="示例文本"
  />
</template>

<script setup lang="ts">
import ReTabs from '@repo/ui/re-tabs';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const tabs = ref<PropOf<typeof ReTabs, 'tabs'>>([{ name: '示例', label: '示例', hint: '示例', icon: '示例', disabled: false, closable: false, status: 'online', dirty: false, badge: '示例' }]);
const contextMenu = ref<PropOf<typeof ReTabs, 'contextMenu'>>([{ key: '示例', label: '示例', icon: '示例', disabled: false, danger: false, divided: false }]);
</script>
