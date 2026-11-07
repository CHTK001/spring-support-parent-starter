<!--
  ReCarousel 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReCarousel
    :items="items"
    :modelValue="0"
    :interval="0"
    :direction="'horizontal'"
    :loop="false"
    :arrow="'hover'"
    :indicator="'always'"
    :pauseOnHover="false"
    :initialIndex="0"
    :indicatorPosition="'inside'"
    itemKey="示例"
    :onChange="onChange"
    :beforeChange="beforeChange"
  />
</template>

<script setup lang="ts">
import ReCarousel from '@repo/ui/re-carousel';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const items = ref<PropOf<typeof ReCarousel, 'items'>>([]);
const onChange = ref<PropOf<typeof ReCarousel, 'onChange'>>((index: any) => undefined);
const beforeChange = ref<PropOf<typeof ReCarousel, 'beforeChange'>>((newIndex: any, oldIndex: any) => Promise.resolve(false));
</script>
