<!--
  ReDatePicker 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReDatePicker
    :type="'year'"
    placeholder="请输入"
    startPlaceholder="示例文本"
    endPlaceholder="示例文本"
    format="YYYY-MM-DD"
    valueFormat="示例"
    :clearable="false"
    :disabled="false"
    :readonly="false"
    :size="'large'"
    rangeSeparator="示例"
    :disabledDate="disabledDate"
    :shortcuts="shortcuts"
  />
</template>

<script setup lang="ts">
import ReDatePicker from '@repo/ui/re-date-picker';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const disabledDate = ref<PropOf<typeof ReDatePicker, 'disabledDate'>>((date: any) => false);
const shortcuts = ref<PropOf<typeof ReDatePicker, 'shortcuts'>>([{ text: '示例', value: new Date() }]);
</script>
