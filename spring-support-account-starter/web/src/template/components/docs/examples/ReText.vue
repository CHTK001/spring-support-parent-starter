<!--
  ReText 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReText
    :type="'primary'"
    :size="'large'"
    :truncated="false"
    :lineClamp="0"
    tag="span"
    text="示例文本"
    :ocr="false"
    :obfuscate="false"
    :security="security"
  />
</template>

<script setup lang="ts">
import ReText from '@repo/ui/re-text';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const security = ref<PropOf<typeof ReText, 'security'>>({ ocr: { fps: 0, pattern: 'stripe', distortion: false }, obfuscate: { baseCodepoint: 0, fontSize: 0, fontFamily: '示例', color: '示例' } });
</script>
