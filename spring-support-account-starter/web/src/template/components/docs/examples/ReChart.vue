<!--
  ReChart 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReChart
    :type="'line'"
    :engine="'echarts'"
    :engineSelector="false"
    height="320px"
    width="100%"
    :theme="'light'"
    minHeight="320px"
    :autoResize="false"
    :data="data"
    :indent="0"
    :collapseDepth="0"
    :sankey="sankey"
    :option="option"
    :categories="categories"
    :values="values"
    name="示例名称"
    :series="series"
    emptyText="示例文本"
    :zoom="false"
    :drag="false"
  />
</template>

<script setup lang="ts">
import ReChart from '@repo/ui/re-chart';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const data = ref<PropOf<typeof ReChart, 'data'>>({ id: '示例', name: '示例', label: '示例', currency: '示例', variableValue: 0, variableUp: false, status: '示例', color: '示例', rate: 0, children: [{ id: '示例', name: '示例', label: '示例', currency: '示例', variableValue: 0, variableUp: false, status: '示例', color: '示例', rate: 0, children: [] }] });
const sankey = ref<PropOf<typeof ReChart, 'sankey'>>({ nodes: undefined, links: undefined });
const option = ref<PropOf<typeof ReChart, 'option'>>({});
const categories = ref<PropOf<typeof ReChart, 'categories'>>([]);
const values = ref<PropOf<typeof ReChart, 'values'>>([]);
const series = ref<PropOf<typeof ReChart, 'series'>>([{ name: '示例', data: [], type: '示例', color: '示例', itemStyle: { '示例': undefined }, areaStyle: { '示例': undefined } }]);
</script>
