<!--
  RePlaceholderPicker 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <RePlaceholderPicker
    :groups="groups"
    :mode="'button'"
    :copyOnPick="false"
    emptyText="示例文本"
  />
</template>

<script setup lang="ts">
import RePlaceholderPicker from '@repo/ui/re-placeholder-picker';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const groups = ref<PropOf<typeof RePlaceholderPicker, 'groups'>>([{ label: '示例', items: [] }]);
</script>
