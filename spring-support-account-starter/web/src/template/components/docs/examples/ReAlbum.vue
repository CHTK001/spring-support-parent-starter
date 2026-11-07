<!--
  ReAlbum 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReAlbum
    :data="data"
    :mode="'grid'"
    :columns="0"
    :gap="0"
    aspectRatio="示例"
    :selectable="false"
    :selectedKeys="selectedKeys"
    :modelValue="modelValue"
    fallbackImage="https://example.com/image.png"
    :items="items"
    srcField="https://example.com/image.png"
    labelField="示例文本"
    :loading="false"
    :toolbar="false"
    :uploadable="false"
    :showUploadSlot="false"
    :pagination="false"
    :totalPages="0"
    :currentPage="0"
  />
</template>

<script setup lang="ts">
import ReAlbum from '@repo/ui/re-album';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const data = ref<PropOf<typeof ReAlbum, 'data'>>([{ id: '示例', src: '示例', thumb: '示例', label: '示例' }]);
const selectedKeys = ref<PropOf<typeof ReAlbum, 'selectedKeys'>>(['示例']);
const modelValue = ref<PropOf<typeof ReAlbum, 'modelValue'>>(['示例']);
const items = ref<PropOf<typeof ReAlbum, 'items'>>([{ id: '示例', src: '示例', thumb: '示例', label: '示例' }]);
</script>
