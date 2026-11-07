<!--
  ReCommand 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReCommand
    modelValue="示例"
    :os="'linux'"
    projectType="示例"
    :scriptKind="'start'"
    :context="context"
    label="示例标签"
    :rows="0"
    placeholder="请输入"
    :ai="false"
    :disabled="false"
    capability="示例"
    :noExpand="false"
  />
</template>

<script setup lang="ts">
import ReCommand from '@repo/ui/re-command';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const context = ref<PropOf<typeof ReCommand, 'context'>>({ entry: '示例', log: '示例', path: '示例', appName: '示例' });
</script>
