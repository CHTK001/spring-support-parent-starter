<!--
  ReRagChat 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReRagChat
    baseUrl="https://example.com/image.png"
    libraryId="示例"
    :defaultTopK="0"
    :defaultThreshold="0"
    :headers="headers"
    :initialMessages="initialMessages"
  />
</template>

<script setup lang="ts">
import ReRagChat from '@repo/ui/re-rag-chat';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const headers = ref<PropOf<typeof ReRagChat, 'headers'>>({ '示例': '示例' });
const initialMessages = ref<PropOf<typeof ReRagChat, 'initialMessages'>>([{ id: '示例', role: 'user', content: '示例', sources: [], timestamp: 0 }]);
</script>
