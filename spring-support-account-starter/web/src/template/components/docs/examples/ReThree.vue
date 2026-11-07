<!--
  ReThree 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReThree
    :width="'示例'"
    :height="'示例'"
    backgroundColor="示例"
    :autoRotate="false"
    :autoRotateSpeed="0"
    :interactive="false"
    :render="render"
    :animate="animate"
  />
</template>

<script setup lang="ts">
import ReThree from '@repo/ui/re-three';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const render = ref<PropOf<typeof ReThree, 'render'>>((ctx: any) => undefined);
const animate = ref<PropOf<typeof ReThree, 'animate'>>((ctx: any) => undefined);
</script>
