<!--
  ReImageGroup 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReImageGroup
    :srcList="srcList"
    :previewSrcList="previewSrcList"
    :layout="'grid'"
    :columns="0"
    :gap="0"
    :radius="0"
    :rowHeight="0"
    :fit="'fill'"
    :preview="false"
    :lazy="false"
    rootMargin="示例"
    :loading="false"
    fallbackText="示例文本"
    emptyText="示例文本"
  />
</template>

<script setup lang="ts">
import ReImageGroup from '@repo/ui/re-image-group';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const srcList = ref<PropOf<typeof ReImageGroup, 'srcList'>>([]);
const previewSrcList = ref<PropOf<typeof ReImageGroup, 'previewSrcList'>>([]);
</script>
