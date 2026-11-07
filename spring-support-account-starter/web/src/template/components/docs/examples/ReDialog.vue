<!--
  ReDialog 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReDialog
    :layout="'el'"
    id="001"
    title="示例标题"
    :width="'示例'"
    top="示例"
    :appendToBody="false"
    :closeOnClickModal="false"
    :closeOnPressEscape="false"
    :showClose="false"
    :beforeClose="beforeClose"
    :destroyOnClose="false"
    :modal="false"
    :draggable="false"
    :overflow="false"
    :fullscreenable="false"
    :fullscreen="false"
    :confirmLoading="false"
    okText="示例文本"
    cancelText="示例文本"
    :showOk="false"
    :showCancel="false"
    :showFooter="false"
    :footerHidden="false"
    :headless="false"
    :responsive="false"
    :mobileBreakpoint="0"
    :mobileFullscreen="false"
    :compact="false"
    color="示例"
    :size="0"
    confirmText="示例文本"
    cancelButtonText="示例文本"
    :minWidth="0"
    :defaultWidth="0"
    :defaultHeight="0"
    :isFullscreen="false"
    :enabled="false"
    :showMinimize="false"
    :showFullscreen="false"
    :visible="false"
    tooltip="示例"
    :stackIndex="0"
    subtitle="示例文本"
    :icon="icon"
    :center="false"
    :alignCenter="false"
    :height="0"
    :loading="false"
    :showWindowShell="false"
    :zIndex="0"
  />
</template>

<script setup lang="ts">
import ReDialog from '@repo/ui/re-dialog';
import { Check } from '@element-plus/icons-vue';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const beforeClose = ref<PropOf<typeof ReDialog, 'beforeClose'>>((done: any) => undefined);
const icon = ref<PropOf<typeof ReDialog, 'icon'>>(Check);
</script>
