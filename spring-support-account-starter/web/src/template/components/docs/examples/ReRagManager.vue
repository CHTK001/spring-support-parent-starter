<!--
  ReRagManager 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReRagManager
    baseUrl="https://example.com/image.png"
    libraryId="示例"
    :headers="headers"
    engine="示例"
    :maxRecursivePages="0"
    storageType="示例"
    bucket="示例"
  />
</template>

<script setup lang="ts">
import ReRagManager from '@repo/ui/re-rag-manager';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const headers = ref<PropOf<typeof ReRagManager, 'headers'>>({ '示例': '示例' });
</script>
