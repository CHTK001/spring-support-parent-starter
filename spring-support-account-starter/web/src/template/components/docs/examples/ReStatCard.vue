<!--
  ReStatCard 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReStatCard
    title="示例标题"
    :value="'示例'"
    desc="示例描述"
    :icon="icon"
    iconColor="示例"
    :size="'default'"
    :layout="'auto'"
  />
</template>

<script setup lang="ts">
import ReStatCard from '@repo/ui/re-stat-card';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const icon = ref<PropOf<typeof ReStatCard, 'icon'>>(Check);
</script>
