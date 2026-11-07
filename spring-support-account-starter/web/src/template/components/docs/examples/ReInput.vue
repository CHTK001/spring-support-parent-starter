<!--
  ReInput 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReInput
    :layout="'el'"
    :type="'text'"
    placeholder="请输入"
    :clearable="false"
    :disabled="false"
    :readonly="false"
    :showPassword="false"
    :showWordLimit="false"
    :maxlength="0"
    :minlength="0"
    :rows="0"
    :autosize="false"
    :size="'large'"
    prefixIcon="示例"
    suffixIcon="示例"
    :status="''"
    :formatter="formatter"
    :parser="parser"
    :focusRight="false"
  />
</template>

<script setup lang="ts">
import ReInput from '@repo/ui/re-input';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const formatter = ref<PropOf<typeof ReInput, 'formatter'>>((value: any) => '示例');
const parser = ref<PropOf<typeof ReInput, 'parser'>>((value: any) => '示例');
</script>
