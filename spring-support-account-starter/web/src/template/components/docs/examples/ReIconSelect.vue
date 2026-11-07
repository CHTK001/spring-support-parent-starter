<!--
  ReIconSelect 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReIconSelect
    :pageSize="0"
    :tabs="tabs"
    :icons="icons"
    placeholder="请输入"
    :popoverWidth="0"
    :popoverHeight="0"
    modelValue="示例"
    width="100%"
    widthPopover="100%"
    :disabled="false"
    iconColor="示例"
    iconBg="示例"
  />
</template>

<script setup lang="ts">
import ReIconSelect from '@repo/ui/re-icon-select';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const tabs = ref<PropOf<typeof ReIconSelect, 'tabs'>>([{ label: '示例', prefix: '示例' }]);
const icons = ref<PropOf<typeof ReIconSelect, 'icons'>>({ '示例': [] });
</script>
