<!--
  ReRemoteSelect 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReRemoteSelect
    url="https://example.com/api"
    :loader="loader"
    :params="params"
    :options="options"
    :enableInput="false"
    :filterable="false"
    :clearable="false"
    :disabled="false"
    placeholder="请输入"
    inputPlaceholder="示例文本"
    :size="'large'"
    :autoReload="false"
    syncIcon="示例"
    syncTooltip="示例"
    :debounceMs="0"
    labelKey="示例文本"
    valueKey="示例"
    :popoverWidth="0"
  />
</template>

<script setup lang="ts">
import ReRemoteSelect from '@repo/ui/re-remote-select';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const loader = ref<PropOf<typeof ReRemoteSelect, 'loader'>>((params: any) => Promise.resolve([{ label: '示例', value: '示例', disabled: false, description: '示例' }]));
const params = ref<PropOf<typeof ReRemoteSelect, 'params'>>({ '示例': undefined });
const options = ref<PropOf<typeof ReRemoteSelect, 'options'>>([{ label: '示例', value: '示例', disabled: false, description: '示例' }]);
</script>
