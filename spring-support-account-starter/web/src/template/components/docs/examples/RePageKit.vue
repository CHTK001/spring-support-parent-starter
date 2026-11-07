<!--
  RePageKit 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <RePageVariantDock
    :items="items"
    :modelValue="'console'"
    pageLabel="示例文本"
    :height="0"
    :ticks="ticks"
    :nowPercent="0"
    axisLabel="示例文本"
    :axisClickable="false"
    :rows="rows"
    :rowKey="rowKey"
    :minWidth="0"
    :clickable="false"
    :loading="false"
    emptyText="示例文本"
    emptyHint="示例"
    :pickedKey="'示例'"
    :groups="groups"
    :activeKey="'示例'"
    :railWidth="0"
    :columns="columns"
    :cells="cells"
    title="示例标题"
    desc="示例描述"
    :variant="'console'"
    :variantItems="variantItems"
    :dockVisible="false"
    :tabs="tabs"
    tabValue="示例"
    :filters="filters"
    :filterModel="filterModel"
    countText="示例文本"
    :showBar="false"
    :noHead="false"
    tabLabel="示例文本"
    refreshTip="示例"
  />
</template>

<script setup lang="ts">
import { RePageVariantDock } from '@repo/ui/re-page-kit';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const items = ref<PropOf<typeof RePageVariantDock, 'items'>>([{ id: 'console', label: '示例', thesis: '示例' }]);
const ticks = ref<PropOf<typeof RePageVariantDock, 'ticks'>>([]);
const rows = ref<PropOf<typeof RePageVariantDock, 'rows'>>([]);
const rowKey = ref<PropOf<typeof RePageVariantDock, 'rowKey'>>((row: any) => '示例');
const groups = ref<PropOf<typeof RePageVariantDock, 'groups'>>([{ key: '示例', label: '示例', rows: [] }]);
const columns = ref<PropOf<typeof RePageVariantDock, 'columns'>>([{ key: '示例', label: '示例', width: '示例', align: 'left' }]);
const cells = ref<PropOf<typeof RePageVariantDock, 'cells'>>([{ label: '示例', value: '示例', tone: 'hot' }]);
const variantItems = ref<PropOf<typeof RePageVariantDock, 'variantItems'>>([{ id: 'console', label: '示例', thesis: '示例' }]);
const tabs = ref<PropOf<typeof RePageVariantDock, 'tabs'>>([{ value: '示例', label: '示例', count: 0 }]);
const filters = ref<PropOf<typeof RePageVariantDock, 'filters'>>([{ key: '示例', kind: 'search', placeholder: '示例', options: [], width: 0 }]);
const filterModel = ref<PropOf<typeof RePageVariantDock, 'filterModel'>>({ '示例': '示例' });
</script>
