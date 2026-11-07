<!--
  ReStat 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReStat
    title="示例标题"
    :value="0"
    :decimals="0"
    :duration="0"
    prefix="¥"
    suffix="元"
    :trend="0"
    :reverseTrend="false"
    description="这是一条示例描述"
    :icon="icon"
    iconColor="示例"
    :size="'small'"
    :loading="false"
  />
</template>

<script setup lang="ts">
import ReStat from '@repo/ui/re-stat';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const icon = ref<PropOf<typeof ReStat, 'icon'>>(Check);
</script>
