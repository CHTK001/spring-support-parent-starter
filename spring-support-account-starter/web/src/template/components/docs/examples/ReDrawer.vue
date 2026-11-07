<!--
  ReDrawer 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReDrawer
    id="001"
    title="示例标题"
    :size="'示例'"
    :direction="'ltr'"
    :appendToBody="false"
    :closeOnClickModal="false"
    :destroyOnClose="false"
    :beforeClose="beforeClose"
    :showClose="false"
    :modal="false"
    :lockScroll="false"
    confirmText="示例文本"
    cancelText="示例文本"
    :confirmLoading="false"
    :loading="false"
    :showOk="false"
    :showCancel="false"
    :showFooter="false"
    :footerHidden="false"
    :isFullscreen="false"
    :enabled="false"
    :closeOnly="false"
    :visible="false"
    tooltip="示例"
    :stackIndex="0"
    :icon="icon"
    :showWindowShell="false"
    :showMinimize="false"
    :showFullscreen="false"
    modalClass="示例"
    drawerClass="示例"
    :closeConfirm="false"
  />
</template>

<script setup lang="ts">
import ReDrawer from '@repo/ui/re-drawer';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const beforeClose = ref<PropOf<typeof ReDrawer, 'beforeClose'>>((done: any) => undefined);
const icon = ref<PropOf<typeof ReDrawer, 'icon'>>(Check);
</script>
