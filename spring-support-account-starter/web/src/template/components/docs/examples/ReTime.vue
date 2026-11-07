<!--
  ReTime 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTime
    :value="value"
    :format="'YYYY-MM-DD HH:mm:ss'"
    :relative="false"
    :refreshInterval="0"
    placeholder="请输入"
    :base="0"
  />
</template>

<script setup lang="ts">
import ReTime from '@repo/ui/re-time';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const value = ref<PropOf<typeof ReTime, 'value'>>(new Date());
</script>
