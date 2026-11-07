<!--
  ReTab 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTab
    :tabs="tabs"
    modelValue="示例"
    :type="'card'"
    :closable="false"
    :stretch="false"
    :variant="'capsule'"
    :tabPosition="'top'"
    :collapsed="false"
    :collapsible="false"
    :navOnly="false"
  />
</template>

<script setup lang="ts">
import ReTab from '@repo/ui/re-tab';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const tabs = ref<PropOf<typeof ReTab, 'tabs'>>([{ name: '示例', label: '示例', icon: '示例', closable: false, disabled: false, badge: '示例' }]);
</script>
