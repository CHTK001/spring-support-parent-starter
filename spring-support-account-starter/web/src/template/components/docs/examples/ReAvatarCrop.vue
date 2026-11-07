<!--
  ReAvatarCrop 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReAvatarCrop
    modelValue="示例"
    action="示例"
    :headers="headers"
    :data="data"
    name="示例名称"
    accept="示例"
    :aspectRatio="0"
    :compress="0"
    outputType="示例"
    fileName="示例"
    triggerText="示例文本"
    dialogTitle="示例文本"
    hintText="示例文本"
    cancelText="示例文本"
    confirmText="示例文本"
    alt="示例"
    :disabled="false"
    :maxSize="0"
    responseUrlPath="https://example.com/image.png"
  />
</template>

<script setup lang="ts">
import ReAvatarCrop from '@repo/ui/re-avatar-crop';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const headers = ref<PropOf<typeof ReAvatarCrop, 'headers'>>({ '示例': '示例' });
const data = ref<PropOf<typeof ReAvatarCrop, 'data'>>({ '示例': undefined });
</script>
