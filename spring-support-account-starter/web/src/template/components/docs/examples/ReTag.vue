<!--
  ReTag 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReTag
    :type="'primary'"
    :size="'large'"
    :closable="false"
    :disableTransitions="false"
    :hit="false"
    color="示例"
    :effect="'dark'"
    :round="false"
    :value="'示例'"
    :map="map"
    icon="示例"
    :dot="false"
    dotColor="示例"
    :tagOptions="tagOptions"
  />
</template>

<script setup lang="ts">
import ReTag from '@repo/ui/re-tag';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const map = ref<PropOf<typeof ReTag, 'map'>>({ '示例': { label: '示例', type: 'primary', color: '示例', icon: '示例' } });
const tagOptions = ref<PropOf<typeof ReTag, 'tagOptions'>>([{ dictValue: '示例', dictLabel: '示例', dictTag: 'primary', dictClass: '示例', icon: '示例' }]);
</script>
