<!--
  ReImage 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReImage
    src="https://example.com/image.png"
    alt="示例"
    :width="'示例'"
    :height="'示例'"
    :radius="'示例'"
    :fit="'fill'"
    :preview="false"
    :previewList="previewList"
    :lazy="false"
    :loading="false"
    fallbackText="示例文本"
  />
</template>

<script setup lang="ts">
import ReImage from '@repo/ui/re-image';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const previewList = ref<PropOf<typeof ReImage, 'previewList'>>([]);
</script>
