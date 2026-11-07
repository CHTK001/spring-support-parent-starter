<!--
  ReProcessTask 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReProcessTask
    :tasks="tasks"
    :autoCollapse="false"
  />
</template>

<script setup lang="ts">
import ReProcessTask from '@repo/ui/re-process-task';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const tasks = ref<PropOf<typeof ReProcessTask, 'tasks'>>([{ taskId: '示例', title: '示例', operation: '示例', phase: '示例', currentStep: '示例', progress: 0, logs: [], finished: false, success: false, message: '示例' }]);
</script>
