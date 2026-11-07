<!--
  ReRelationGraph 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReRelationGraph
    :nodes="nodes"
    :edges="edges"
    :height="'示例'"
    :layout="'group'"
    :layoutOptions="layoutOptions"
    :groupType="'bubble'"
    :groupVisible="false"
    :groupAutoHide="false"
    :groupOptions="groupOptions"
    :toolbar="false"
    :toolbarVariant="'pill'"
    :toolbarPosition="'top-left'"
    :toolbarCollapsible="false"
    :tooltip="false"
    :nodeTooltip="false"
    :edgeTooltip="false"
    :tooltipOptions="tooltipOptions"
    :rightClickSelect="false"
    :nodeSize="0"
    emptyText="示例文本"
    :nodeBadge="false"
    :edgeArrow="false"
    :edgeDashed="false"
    :edgeWidth="0"
    :edgeLabel="false"
    :clickSelect="false"
    :brushSelect="false"
    :highlight="false"
    :minimap="false"
    :combo="false"
    :collapsedGroups="collapsedGroups"
    :history="false"
    :historyLimit="0"
    :keyboard="false"
    :shortcuts="shortcuts"
    :groupAutoHidden="false"
    :variant="'pill'"
    :fitButton="false"
    :collapsible="false"
    :position="'top-left'"
    :pathNodes="pathNodes"
    pathFrom="https://example.com/image.png"
    pathTo="https://example.com/image.png"
    :pathActive="false"
    :pathPicking="false"
    pathInfo="https://example.com/image.png"
    :groups="groups"
    :canUndo="false"
    :canRedo="false"
  />
</template>

<script setup lang="ts">
import ReRelationGraph from '@repo/ui/re-relation-graph';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const nodes = ref<PropOf<typeof ReRelationGraph, 'nodes'>>([{ id: '示例', label: '示例', group: '示例', type: '示例', size: 0, image: '示例', imageRadius: 0, badge: '示例', badgeColor: '示例' }]);
const edges = ref<PropOf<typeof ReRelationGraph, 'edges'>>([{ id: '示例', source: '示例', target: '示例', label: '示例', width: 0, dashed: false, arrow: false, color: '示例' }]);
const layoutOptions = ref<PropOf<typeof ReRelationGraph, 'layoutOptions'>>({ '示例': undefined });
const groupOptions = ref<PropOf<typeof ReRelationGraph, 'groupOptions'>>({ '示例': undefined });
const tooltipOptions = ref<PropOf<typeof ReRelationGraph, 'tooltipOptions'>>({ getContent: (data: any, kind: any) => '示例' });
const collapsedGroups = ref<PropOf<typeof ReRelationGraph, 'collapsedGroups'>>([]);
const shortcuts = ref<PropOf<typeof ReRelationGraph, 'shortcuts'>>({ '示例': '示例' });
const pathNodes = ref<PropOf<typeof ReRelationGraph, 'pathNodes'>>([]);
const groups = ref<PropOf<typeof ReRelationGraph, 'groups'>>([]);
</script>
