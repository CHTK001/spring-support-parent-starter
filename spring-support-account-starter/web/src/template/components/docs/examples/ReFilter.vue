<!--
  ReFilter 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReFilter
    :items="items"
    :columns="0"
    :collapsible="false"
    :expanded="false"
    :showActions="false"
    searchText="示例文本"
    resetText="示例文本"
    :drawerEnabled="false"
    drawerTitle="示例文本"
    :drawerSize="'示例'"
  />
</template>

<script setup lang="ts">
import ReFilter from '@repo/ui/re-filter';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const items = ref<PropOf<typeof ReFilter, 'items'>>([{ prop: '示例', label: '示例', type: 'input', placeholder: '示例', options: [], dataSource: '示例', url: '示例', loader: (params: any) => Promise.resolve([{ label: '示例', value: '示例', disabled: false, icon: '示例', activeIcon: '示例', inactiveIcon: '示例', image: '示例', tip: '示例', description: '示例', tag: '示例', tagColor: '示例', premium: false, consumption: '示例', group: '示例', preset: '示例' }]), multiple: false, clearable: false, defaultValue: undefined, always: false, disabled: false, span: 0 }]);
</script>
