<!--
  ReStatsCard 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReStatsCard
    :list="list"
    :columns="0"
    :clickable="false"
    :duration="0"
  />
</template>

<script setup lang="ts">
import ReStatsCard from '@repo/ui/re-stats-card';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const list = ref<PropOf<typeof ReStatsCard, 'list'>>([{ key: '示例', title: '示例', value: '示例', unit: '示例', desc: '示例', sub: '示例', icon: Check, iconColor: '示例', valueColor: '示例', payload: undefined, target: '示例', ringPercent: 0, ringColor: '示例', level: 'ok', series: [], animate: false, duration: 0, precision: 0 }]);
</script>
