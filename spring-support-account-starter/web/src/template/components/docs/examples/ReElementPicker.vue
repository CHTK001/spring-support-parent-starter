<!--
  ReElementPicker 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReElementPicker
    :visible="false"
    url="https://example.com/api"
    parserType="示例"
    :loader="loader"
    :navNonce="0"
  />
</template>

<script setup lang="ts">
import ReElementPicker from '@repo/ui/re-element-picker';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const loader = ref<PropOf<typeof ReElementPicker, 'loader'>>((url: any) => Promise.resolve({ status: 0, contentType: '示例', html: '示例' }));
</script>
