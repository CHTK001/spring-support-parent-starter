<!--
  ReTimeline 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTimeline
    :data="data"
    :placement="'left'"
    :activeKey="'示例'"
    :items="items"
    :activeIndex="0"
    :mode="'left'"
    :size="'small'"
    :showLine="false"
  />
</template>

<script setup lang="ts">
import ReTimeline from '@repo/ui/re-timeline';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const data = ref<PropOf<typeof ReTimeline, 'data'>>([{ id: '示例', title: '示例', description: '示例', time: '示例', dotColor: '示例', dotType: 'primary' }]);
const items = ref<PropOf<typeof ReTimeline, 'items'>>([{ id: '示例', title: '示例', description: '示例', time: '示例', dotColor: '示例', dotType: 'primary' }]);
</script>
