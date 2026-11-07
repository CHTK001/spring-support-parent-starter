<!--
  ReFlowGraph 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReFlowGraph
    :node="node"
    :graph="graph"
    :data="data"
    :nodeTypes="nodeTypes"
    :readonly="false"
    :height="'示例'"
    :width="'示例'"
    :showToolbar="false"
    :showPalette="false"
    :showProperties="false"
    startType="示例"
    endType="示例"
    propertiesTitle="示例文本"
    :showMinimap="false"
    :panning="false"
    :zoom="false"
    :nodeDraggable="false"
    :menuItems="menuItems"
    :nodeComponent="nodeComponent"
    :editable="false"
  />
</template>

<script setup lang="ts">
import ReFlowGraph from '@repo/ui/re-flow-graph';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const node = ref<PropOf<typeof ReFlowGraph, 'node'>>({});
const graph = ref<PropOf<typeof ReFlowGraph, 'graph'>>({});
const data = ref<PropOf<typeof ReFlowGraph, 'data'>>({ nodes: [{ id: '示例', type: '示例', label: '示例', x: 0, y: 0, data: { '示例': undefined } }], edges: [{ id: '示例', from: '示例', to: '示例', fromPort: '示例', toPort: '示例', kind: 'normal', label: '示例', transfer: false, data: { '示例': undefined } }] });
const nodeTypes = ref<PropOf<typeof ReFlowGraph, 'nodeTypes'>>([{ type: '示例', label: '示例', group: '示例', path: '示例', image: '示例', color: '示例', tips: '示例', fixed: false }]);
const menuItems = ref<PropOf<typeof ReFlowGraph, 'menuItems'>>([{ key: '示例', label: '示例', action: 'rename', addType: '示例', children: [], onClick: (ctx: any) => undefined, danger: false, disabled: false, header: '示例' }]);
const nodeComponent = ref<PropOf<typeof ReFlowGraph, 'nodeComponent'>>(Check);
</script>
