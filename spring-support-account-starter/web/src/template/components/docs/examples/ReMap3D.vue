<!--
  ReMap3D 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReMap3D
    :engine="'mars3d'"
    :pins="pins"
    :location="location"
    :height="0"
    :autoRotate="false"
    :showFooter="false"
    :showStatus="false"
    :debugEnabled="false"
    :supportFullscreen="false"
    :enableExportImage="false"
    :autoResetOnResize="false"
  />
</template>

<script setup lang="ts">
import ReMap3D from '@repo/ui/re-map3d';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const pins = ref<PropOf<typeof ReMap3D, 'pins'>>([{ name: '示例', lat: 0, lng: 0, type: '示例', color: '示例', value: 0 }]);
const location = ref<PropOf<typeof ReMap3D, 'location'>>({ lat: 0, lng: 0, zoom: 0 });
</script>
