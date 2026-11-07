<!--
  ReTree 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTree
    :data="data"
    url="https://example.com/api"
    :loader="loader"
    :defaultExpandedKeys="defaultExpandedKeys"
    nodeKey="示例"
    emptyText="示例文本"
    :renderAfterExpand="false"
    :checkStrictly="false"
    :defaultExpandAll="false"
    :expandOnClickNode="false"
    :checkOnClickNode="false"
    :checkDescendants="false"
    :autoExpandParent="false"
    :defaultCheckedKeys="defaultCheckedKeys"
    :currentNodeKey="'示例'"
    :showCheckbox="false"
    :draggable="false"
    :allowDrag="allowDrag"
    :allowDrop="allowDrop"
    :props="props"
    :lazy="false"
    :highlightCurrent="false"
    :load="load"
    :filterNodeMethod="filterNodeMethod"
    :accordion="false"
    :indent="0"
    :icon="'示例'"
    highlightClass="示例"
    :editable="false"
    :showToolbar="false"
    :enableAdd="false"
    :enableEdit="false"
    :enableRemove="false"
    :enableExpandAll="false"
    :enableCollapseAll="false"
    keyField="示例"
    labelField="示例文本"
    iconField="示例"
    :toolbar="false"
  />
</template>

<script setup lang="ts">
import ReTree from '@repo/ui/re-tree';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const data = ref<PropOf<typeof ReTree, 'data'>>([{ id: '示例', label: '示例', children: [], disabled: false, bgColor: '示例' }]);
const loader = ref<PropOf<typeof ReTree, 'loader'>>((node: any) => Promise.resolve([{ id: '示例', label: '示例', children: [], disabled: false, bgColor: '示例' }]));
const defaultExpandedKeys = ref<PropOf<typeof ReTree, 'defaultExpandedKeys'>>(['示例']);
const defaultCheckedKeys = ref<PropOf<typeof ReTree, 'defaultCheckedKeys'>>(['示例']);
const allowDrag = ref<PropOf<typeof ReTree, 'allowDrag'>>((node: any) => false);
const allowDrop = ref<PropOf<typeof ReTree, 'allowDrop'>>((draggingNode: any, dropNode: any, type: any) => false);
const props = ref<PropOf<typeof ReTree, 'props'>>({ children: '示例', label: '示例', disabled: '示例', isLeaf: '示例', class: '示例' });
const load = ref<PropOf<typeof ReTree, 'load'>>((node: any, resolve: any) => undefined);
const filterNodeMethod = ref<PropOf<typeof ReTree, 'filterNodeMethod'>>((value: any, data: any, node: any) => false);
</script>
