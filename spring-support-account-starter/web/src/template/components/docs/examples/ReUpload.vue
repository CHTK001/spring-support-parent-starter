<!--
  ReUpload 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReUpload
    :modelValue="modelValue"
    action="示例"
    :headers="headers"
    :data="data"
    name="示例名称"
    accept="示例"
    :type="'image'"
    :drag="false"
    :multiple="false"
    :listType="'text'"
    :showFileList="false"
    :limit="0"
    :maxSize="0"
    triggerText="示例文本"
    dragText="示例文本"
    dragHint="示例"
    tipText="示例文本"
    :disabled="false"
    responseUrlPath="https://example.com/image.png"
    fileName="示例"
  />
</template>

<script setup lang="ts">
import ReUpload from '@repo/ui/re-upload';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const modelValue = ref<PropOf<typeof ReUpload, 'modelValue'>>([{ name: '示例', url: '示例', uid: '示例', status: 'ready', response: undefined, raw: new File([], '示例.txt') }]);
const headers = ref<PropOf<typeof ReUpload, 'headers'>>({ '示例': '示例' });
const data = ref<PropOf<typeof ReUpload, 'data'>>({ '示例': undefined });
</script>
