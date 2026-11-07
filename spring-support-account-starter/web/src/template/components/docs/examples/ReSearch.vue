<!--
  ReSearch 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReSearch
    :filterVisible="false"
    :btnMode="'icon'"
    :showToggle="false"
    :showSearch="false"
    :showReset="false"
    :showRefresh="false"
    :showAdd="false"
    :showBatchDelete="false"
    :showBatchSelect="false"
    searchText="示例文本"
    resetText="示例文本"
    addText="示例文本"
    batchDeleteText="示例文本"
    batchSelectText="示例文本"
    :batchDeleteDisabled="false"
    :batchSelectActive="false"
    batchSelectTextActive="示例文本"
    :loading="false"
    :isExpanded="false"
    :bordered="false"
    :mode="1"
    :showLabel="false"
    tooltipText="示例文本"
    padding="示例"
    :addButton="false"
    :layout="'default'"
    :filterMode="'form'"
    title="示例标题"
    :showSearchBtn="false"
    :showResetBtn="false"
    :showBatchDeleteBtn="false"
    :showBatchSelectBtn="false"
    :max="0"
    :filters="filters"
    :modelValue="modelValue"
  />
</template>

<script setup lang="ts">
import ReSearch from '@repo/ui/re-search';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const filters = ref<PropOf<typeof ReSearch, 'filters'>>([{ key: '示例', label: '示例', type: 'input', placeholder: '示例', options: [], dataSource: '示例', url: '示例', loader: (params: any) => Promise.resolve([{ label: '示例', value: '示例', disabled: false, icon: '示例', activeIcon: '示例', inactiveIcon: '示例', image: '示例', tip: '示例', description: '示例', tag: '示例', tagColor: '示例', premium: false, consumption: '示例', group: '示例', preset: '示例' }]), attrs: { '示例': undefined } }]);
const modelValue = ref<PropOf<typeof ReSearch, 'modelValue'>>({ '示例': undefined });
</script>
