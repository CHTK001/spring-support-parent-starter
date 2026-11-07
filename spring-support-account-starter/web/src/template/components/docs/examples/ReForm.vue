<!--
  ReForm 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReForm
    :layout="'el'"
    :fields="fields"
    :labelWidth="'示例'"
    labelPosition="示例文本"
    :inline="false"
    :disabled="false"
    :rules="rules"
    :gutter="0"
    :field="field"
    :value="undefined"
    type="primary"
  />
</template>

<script setup lang="ts">
import ReForm from '@repo/ui/re-form';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const fields = ref<PropOf<typeof ReForm, 'fields'>>([{ prop: '示例', label: '示例', component: 'input', placeholder: '示例', options: [], dataSource: '示例', required: false, rules: [], hidden: false, disabled: false, span: 0, attrs: { '示例': undefined }, format: (value: any) => undefined, help: '示例', defaultValue: undefined }]);
const rules = ref<PropOf<typeof ReForm, 'rules'>>({ '示例': [] });
const field = ref<PropOf<typeof ReForm, 'field'>>({});
</script>
