<!--
  ReImageCompare 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReImageCompare
    leftImage="https://example.com/image.png"
    rightImage="https://example.com/image.png"
    leftLabel="示例文本"
    rightLabel="示例文本"
    :leftImageCss="leftImageCss"
    :rightImageCss="rightImageCss"
    handle="示例"
    slider="示例"
    :hover="false"
    :defaultSliderHandle="0"
  />
</template>

<script setup lang="ts">
import ReImageCompare from '@repo/ui/re-image-compare';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const leftImageCss = ref<PropOf<typeof ReImageCompare, 'leftImageCss'>>({ '示例': '示例' });
const rightImageCss = ref<PropOf<typeof ReImageCompare, 'rightImageCss'>>({ '示例': '示例' });
</script>
