<!--
  ReLoader 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReLoader
    :loading="false"
    :variant="'progress'"
    :mode="'overlay'"
    text="示例文本"
    :percent="0"
    :size="'sm'"
    color="示例"
    :fullscreen="false"
    :mask="false"
    :closable="false"
    :delay="0"
    :minDuration="0"
    :timeout="0"
    skeletonHeight="320px"
    :skeletonShape="skeletonShape"
    :closeOnClickMask="false"
    :failed="false"
    errorMessage="示例文本"
    errorText="示例文本"
    :retrying="false"
    :cancelOnEsc="false"
    :showPercent="false"
  />
</template>

<script setup lang="ts">
import ReLoader from '@repo/ui/re-loader';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const skeletonShape = ref<PropOf<typeof ReLoader, 'skeletonShape'>>({ layout: 'list', rows: 0, columns: 0, avatar: false, title: false, rowHeight: 0, gap: 0 });
</script>
