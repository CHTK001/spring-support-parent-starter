<!--
  ReSelect 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReSelect
    :data="data"
    :options="options"
    url="https://example.com/api"
    :loader="loader"
    :params="params"
    :cache="false"
    :cacheTtl="0"
    cacheKey="示例"
    :cacheStorage="'local'"
    :layout="'default'"
    placeholder="请输入"
    :clearable="false"
    :filterable="false"
    :allowCreate="false"
    :multiple="false"
    :collapseTags="false"
    :disabled="false"
    :loading="false"
    :type="'large'"
    :size="'large'"
    :popoverWidth="0"
    :tableColumns="tableColumns"
    :autoReload="false"
    title="示例标题"
    :triggerIcon="false"
    :iconOnly="false"
    :vertical="false"
    activeIcon="示例"
    inactiveIcon="示例"
    preset="示例"
    :compact="false"
    :bulkActions="false"
    :controlSize="'large'"
    emptyText="示例文本"
    :modelValue="'示例'"
  />
</template>

<script setup lang="ts">
import ReSelect from '@repo/ui/re-select';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const data = ref<PropOf<typeof ReSelect, 'data'>>([{ label: '示例', value: '示例', disabled: false, icon: '示例', activeIcon: '示例', inactiveIcon: '示例', image: '示例', tip: '示例', description: '示例', tag: '示例', tagColor: '示例', premium: false, consumption: '示例', group: '示例', preset: '示例' }]);
const options = ref<PropOf<typeof ReSelect, 'options'>>([{ label: '示例', value: '示例', disabled: false, icon: '示例', activeIcon: '示例', inactiveIcon: '示例', image: '示例', tip: '示例', description: '示例', tag: '示例', tagColor: '示例', premium: false, consumption: '示例', group: '示例', preset: '示例' }]);
const loader = ref<PropOf<typeof ReSelect, 'loader'>>((params: any) => Promise.resolve([{ label: '示例', value: '示例', disabled: false, icon: '示例', activeIcon: '示例', inactiveIcon: '示例', image: '示例', tip: '示例', description: '示例', tag: '示例', tagColor: '示例', premium: false, consumption: '示例', group: '示例', preset: '示例' }]));
const params = ref<PropOf<typeof ReSelect, 'params'>>({ '示例': undefined });
const tableColumns = ref<PropOf<typeof ReSelect, 'tableColumns'>>([{ key: '示例', label: '示例', width: '示例' }]);
</script>
