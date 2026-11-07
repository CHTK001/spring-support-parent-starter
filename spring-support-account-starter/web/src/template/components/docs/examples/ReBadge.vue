<!--
  ReBadge 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReBadge
    :value="'示例'"
    :max="0"
    :isDot="false"
    :hidden="false"
    :type="'primary'"
    :showZero="false"
    color="示例"
    :offset="offset"
    :badgeStyle="'示例'"
    badgeClass="示例"
    :breath="false"
  />
</template>

<script setup lang="ts">
import ReBadge from '@repo/ui/re-badge';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const offset = ref<PropOf<typeof ReBadge, 'offset'>>([0, 0]);
</script>
