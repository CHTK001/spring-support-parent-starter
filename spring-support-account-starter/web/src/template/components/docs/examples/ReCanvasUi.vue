<!--
  ReCanvasUi 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReCanvasUi
    :simResolution="0"
    :dyeResolution="0"
    :densityDissipation="0"
    :velocityDissipation="0"
    :pressure="0"
    :pressureIterations="0"
    :curl="0"
    :radius="0"
    :force="0"
    :intensity="0"
    :distortion="0"
    :blend="0"
    :color="color"
    :rainbow="false"
    :style="style"
  />
</template>

<script setup lang="ts">
import ReCanvasUi from '@repo/ui/re-canvas-ui';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const color = ref<PropOf<typeof ReCanvasUi, 'color'>>([0, 0, 0]);
const style = ref<PropOf<typeof ReCanvasUi, 'style'>>({ '示例': '示例' });
</script>
