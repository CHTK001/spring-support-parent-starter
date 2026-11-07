<!--
  ReRichText 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReRichText
    modelValue="示例"
    placeholder="请输入"
    :readonly="false"
    :node="node"
    :selected="false"
    :updateAttributes="updateAttributes"
    :deleteNode="deleteNode"
  />
</template>

<script setup lang="ts">
import ReRichText from '@repo/ui/re-rich-text';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const node = ref<PropOf<typeof ReRichText, 'node'>>({ attrs: { latex: '示例' } });
const updateAttributes = ref<PropOf<typeof ReRichText, 'updateAttributes'>>((attrs: any) => undefined);
const deleteNode = ref<PropOf<typeof ReRichText, 'deleteNode'>>(() => undefined);
</script>
