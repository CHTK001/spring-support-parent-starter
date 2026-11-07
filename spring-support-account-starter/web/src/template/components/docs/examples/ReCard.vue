<!--
  ReCard 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReCard
    :layout="'default'"
    header="示例"
    title="示例标题"
    subtitle="示例文本"
    :shadow="'always'"
    :bodyStyle="bodyStyle"
    :radius="0"
    :hoverable="false"
    :bordered="false"
    :padding="0"
    :isFlex="false"
  />
</template>

<script setup lang="ts">
import ReCard from '@repo/ui/re-card';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const bodyStyle = ref<PropOf<typeof ReCard, 'bodyStyle'>>({ '示例': '示例' });
</script>
