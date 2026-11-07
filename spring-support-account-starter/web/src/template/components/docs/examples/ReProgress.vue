<!--
  ReProgress 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReProgress
    :progress="0"
    :color="'primary'"
    :status="'pending'"
    :showText="false"
    :height="0"
    :stripe="false"
    :floating="false"
    :position="'top-right'"
    mode="batch"
    :visibleTasks="visibleTasks"
    :offlineCount="0"
    :showSettings="false"
    :task="task"
    :collapsed="false"
  />
</template>

<script setup lang="ts">
import ReProgress from '@repo/ui/re-progress';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const visibleTasks = ref<PropOf<typeof ReProgress, 'visibleTasks'>>([]);
const task = ref<PropOf<typeof ReProgress, 'task'>>({ id: '示例', title: '示例', description: '示例', category: 'download', color: 'primary', status: 'pending', progress: 0, text: '示例', history: [{ time: 0, progress: 0, text: '示例', status: 'pending' }], createdAt: 0, startedAt: 0, finishedAt: 0, error: '示例', closable: false, icon: '示例', hasHistory: false });
</script>
