<!--
  ReImageGen 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReImageGen
    baseUrl="https://example.com/image.png"
    :headers="headers"
  />
</template>

<script setup lang="ts">
import ReImageGen from '@repo/ui/re-image-gen';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const headers = ref<PropOf<typeof ReImageGen, 'headers'>>({ '示例': '示例' });
</script>
