<!--
  ReBreadcrumb 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReBreadcrumb
    :items="items"
    :separator="'示例'"
    :size="'small'"
    ariaLabel="示例文本"
    :block="false"
  />
</template>

<script setup lang="ts">
import ReBreadcrumb from '@repo/ui/re-breadcrumb';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const items = ref<PropOf<typeof ReBreadcrumb, 'items'>>([{ key: '示例', label: '示例', href: '示例', icon: '示例', to: '示例', disabled: false }]);
</script>
