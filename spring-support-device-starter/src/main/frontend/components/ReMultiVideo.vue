<template>
  <el-dialog :model-value="modelValue" title="多路视频" width="80%" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <div class="flex items-center gap-4 mb-4">
      <span class="text-sm">布局：</span>
      <el-radio-group :model-value="layout" size="small" @update:model-value="(v: number) => emit('update:layout', v)">
        <el-radio-button :value="1">1</el-radio-button>
        <el-radio-button :value="4">4</el-radio-button>
        <el-radio-button :value="9">9</el-radio-button>
        <el-radio-button :value="16">16</el-radio-button>
      </el-radio-group>
      <span class="text-sm text-el-text-secondary ml-4">每个格子独立选择通道</span>
    </div>
    <div class="grid gap-2" :style="`grid-template-columns: repeat(${Math.sqrt(layout)}, 1fr)`">
      <div
        v-for="i in layout"
        :key="i"
        class="aspect-video bg-black rounded-xl overflow-hidden relative"
      >
        <ReVideo :url="urls[i-1]" class="w-full h-full" />
        <el-select
          :model-value="channels[i-1]"
          size="small"
          class="absolute top-2 left-2 w-28"
          @update:model-value="(v: number) => emit('channelChange', { index: i-1, value: v })"
        >
          <el-option
            v-for="ch in channelList"
            :key="ch.deviceChannelId || ch.deviceChannelNo"
            :label="ch.deviceChannelName || `通道 ${ch.deviceChannelNo}`"
            :value="ch.deviceChannelNo"
          />
        </el-select>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import ReVideo from './ReVideo.vue'
import type { DeviceChannel } from '../types/device'

/**
 * 组件属性
 */
defineProps<{
  modelValue: boolean
  layout: number
  urls: string[]
  channels: number[]
  channelList: DeviceChannel[]
}>()

/**
 * 事件
 */
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'update:layout', val: number): void
  (e: 'channelChange', payload: { index: number; value: number }): void
}>()
</script>