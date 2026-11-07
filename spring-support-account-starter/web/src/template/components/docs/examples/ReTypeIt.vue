<!--
  ReTypeIt 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTypeIt
    :strings="strings"
    text="示例文本"
    :speed="0"
    :deleteSpeed="0"
    :pause="0"
    :loop="false"
    :html="false"
    tag="span"
    cursorChar="示例"
    :cursor="false"
    :autoplay="false"
    :startDelay="0"
    :startIndex="0"
    :preDelete="false"
  />
</template>

<script setup lang="ts">
import ReTypeIt from '@repo/ui/re-type-it';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const strings = ref<PropOf<typeof ReTypeIt, 'strings'>>([]);
</script>
