<!--
  RePagination 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <RePagination
    :layout="'el'"
    :total="0"
    :currentPage="0"
    :pageSize="0"
    :pageSizes="pageSizes"
    :disabled="false"
    :background="false"
    paginationLayout="示例"
    :small="false"
    :size="'large'"
  />
</template>

<script setup lang="ts">
import RePagination from '@repo/ui/re-pagination';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const pageSizes = ref<PropOf<typeof RePagination, 'pageSizes'>>([]);
</script>
