<template>
  <div class="re-ptz flex flex-col items-center space-y-4">
    <!-- 摇杆 -->
    <div class="grid grid-cols-3 gap-2 w-48">
      <div></div>
      <el-button circle size="small" @mousedown="emit('move', 'UP')" @mouseup="emit('move', 'STOP')">↑</el-button>
      <div></div>
      <el-button circle size="small" @mousedown="emit('move', 'LEFT')" @mouseup="emit('move', 'STOP')">←</el-button>
      <el-button circle size="small" type="danger" @click="emit('move', 'STOP')">●</el-button>
      <el-button circle size="small" @mousedown="emit('move', 'RIGHT')" @mouseup="emit('move', 'STOP')">→</el-button>
      <div></div>
      <el-button circle size="small" @mousedown="emit('move', 'DOWN')" @mouseup="emit('move', 'STOP')">↓</el-button>
      <div></div>
    </div>

    <!-- 变倍变焦 -->
    <div class="flex items-center gap-4">
      <span class="text-sm text-el-text-secondary">变倍</span>
      <el-slider :model-value="zoom" :min="0" :max="100" style="width:120px" @input="(v: number) => emit('zoom', v)" />
      <span class="text-sm text-el-text-secondary">变焦</span>
      <el-slider :model-value="focus" :min="0" :max="100" style="width:120px" @input="(v: number) => emit('focus', v)" />
    </div>

    <!-- 预置位 -->
    <div class="w-full max-w-md">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium text-el-text">预置位</span>
        <el-button size="small" type="primary" @click="emit('addPreset')">
          <el-icon class="mr-1"><Plus /></el-icon>添加
        </el-button>
      </div>
      <div class="space-y-1 mt-2">
        <div
          v-for="p in presets"
          :key="p.devicePresetNo"
          class="flex items-center justify-between p-2 border-b border-el-border"
        >
          <span class="text-sm text-el-text">{{ p.devicePresetName }}</span>
          <div>
            <el-button size="small" text @click="emit('callPreset', p.devicePresetNo)">调用</el-button>
            <el-button size="small" text type="danger" @click="emit('deletePreset', p.devicePresetNo)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="presets.length === 0" description="暂无预置位" :image-size="50" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import type { Preset } from '../types/device'

/**
 * 组件属性
 */
defineProps<{
  presets: Preset[]
  zoom?: number
  focus?: number
}>()

/**
 * 事件
 */
const emit = defineEmits<{
  (e: 'move', cmd: string): void
  (e: 'zoom', v: number): void
  (e: 'focus', v: number): void
  (e: 'addPreset'): void
  (e: 'callPreset', id: number): void
  (e: 'deletePreset', id: number): void
}>()
</script>