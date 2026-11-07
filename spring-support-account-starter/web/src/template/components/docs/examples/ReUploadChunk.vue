<!--
  ReUploadChunk 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReUploadChunk
    :modelValue="modelValue"
    baseUrl="https://example.com/image.png"
    :headers="headers"
    storageType="示例"
    bucket="示例"
    :chunkSize="0"
    accept="示例"
    :multiple="false"
    :drag="false"
    triggerText="示例文本"
    dragText="示例文本"
    dragHint="示例"
    :disabled="false"
    :hideTrigger="false"
  />
</template>

<script setup lang="ts">
import ReUploadChunk from '@repo/ui/re-upload-chunk';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const modelValue = ref<PropOf<typeof ReUploadChunk, 'modelValue'>>([{ file: new File([], '示例.txt'), uploadId: '示例', key: '示例', totalChunks: 0, chunkSize: 0, uploadedChunks: 0, percent: 0, status: 'pending', url: '示例', error: '示例', name: '示例', size: 0 }]);
const headers = ref<PropOf<typeof ReUploadChunk, 'headers'>>({ '示例': '示例' });
</script>
