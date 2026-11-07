<!--
  ReFilePicker 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReFilePicker
    :modelValue="false"
    title="示例标题"
    :loader="loader"
    :data="data"
    :multiple="false"
    :limit="0"
    accept="示例"
    initialPath="https://example.com/image.png"
    :directoryMode="false"
    :contextMenu="false"
    :previewUrl="previewUrl"
    :createDir="createDir"
    :renameItem="renameItem"
    :removeItems="removeItems"
    :uploadFiles="uploadFiles"
    :downloadItems="downloadItems"
    :view="'details'"
  />
</template>

<script setup lang="ts">
import ReFilePicker from '@repo/ui/re-file-picker';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const loader = ref<PropOf<typeof ReFilePicker, 'loader'>>((path: any) => Promise.resolve([{ key: '示例', name: '示例', isDir: false, size: 0, updatedAt: 0, thumbnail: '示例', mimeType: '示例' }]));
const data = ref<PropOf<typeof ReFilePicker, 'data'>>([{ key: '示例', name: '示例', isDir: false, size: 0, updatedAt: 0, thumbnail: '示例', mimeType: '示例' }]);
const previewUrl = ref<PropOf<typeof ReFilePicker, 'previewUrl'>>((item: any) => Promise.resolve('示例'));
const createDir = ref<PropOf<typeof ReFilePicker, 'createDir'>>((dir: any, name: any) => Promise.resolve(undefined));
const renameItem = ref<PropOf<typeof ReFilePicker, 'renameItem'>>((item: any, newName: any) => Promise.resolve(undefined));
const removeItems = ref<PropOf<typeof ReFilePicker, 'removeItems'>>((items: any) => Promise.resolve(undefined));
const uploadFiles = ref<PropOf<typeof ReFilePicker, 'uploadFiles'>>((dir: any, files: any) => Promise.resolve(undefined));
const downloadItems = ref<PropOf<typeof ReFilePicker, 'downloadItems'>>((items: any) => Promise.resolve(undefined));
</script>
