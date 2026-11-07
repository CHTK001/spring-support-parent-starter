<!--
  ReEcharts 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReEcharts
    :option="option"
    height="320px"
    width="100%"
    :theme="'light'"
    :autoResize="false"
    :notMerge="false"
  />
</template>

<script setup lang="ts">
import ReEcharts from '@repo/ui/re-echarts';
import { shallowRef } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const option = shallowRef<PropOf<typeof ReEcharts, 'option'>>({});
</script>
