<!--
  ReAsync 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReAsync
    :loader="loader"
    minHeight="320px"
    skeletonHeight="320px"
    enterTransition="示例"
    :fillHeight="false"
    :loadingMode="'loader'"
    :loaderVariant="'progress'"
    loadingText="示例文本"
    :loaderDelay="0"
    :loaderTimeout="0"
  />
</template>

<script setup lang="ts">
import ReAsync from '@repo/ui/re-async';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const loader = ref<PropOf<typeof ReAsync, 'loader'>>(() => Promise.resolve({ default: undefined }));
</script>
