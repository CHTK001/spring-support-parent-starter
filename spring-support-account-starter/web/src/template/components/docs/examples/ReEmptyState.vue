<!--
  ReEmptyState 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReEmptyState
    title="示例标题"
    description="这是一条示例描述"
    :icon="icon"
    :iconSize="0"
    :loading="false"
    :size="'compact'"
  />
</template>

<script setup lang="ts">
import ReEmptyState from '@repo/ui/re-empty-state';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const icon = ref<PropOf<typeof ReEmptyState, 'icon'>>(Check);
</script>
