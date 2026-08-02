<template>
  <div class="re-access space-y-4">
    <!-- 门列表 -->
    <div class="grid grid-cols-3 gap-4">
      <el-card
        v-for="door in doors"
        :key="door.deviceDoorNo"
        class="shadow-sm rounded-xl p-4"
      >
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-el-text">{{ door.deviceDoorName }}</span>
          <el-button size="small" type="primary" @click="emit('unlock', door.deviceDoorNo)">开门</el-button>
        </div>
        <div class="text-xs text-el-text-secondary mt-1">状态: {{ door.deviceDoorStatus }}</div>
      </el-card>
      <el-empty v-if="doors.length === 0" description="无门禁" :image-size="50" />
    </div>

    <!-- 门禁事件 -->
    <el-card class="shadow-sm rounded-xl p-4">
      <div class="flex items-center justify-between mb-2">
        <span class="text-sm font-medium text-el-text">门禁事件</span>
        <el-button size="small" text @click="emit('refresh')">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新
        </el-button>
      </div>
      <el-table :data="events" size="small" style="width:100%">
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column prop="doorName" label="门" />
        <el-table-column prop="eventType" label="事件类型" />
        <el-table-column prop="cardNo" label="卡号" />
        <el-empty v-if="events.length === 0" description="无事件" :image-size="40" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import type { DeviceDoor, AccessEvent } from '../types/device'

/**
 * 组件属性
 */
defineProps<{
  doors: DeviceDoor[]
  events: AccessEvent[]
}>()

/**
 * 事件
 */
const emit = defineEmits<{
  (e: 'unlock', doorId: number): void
  (e: 'refresh'): void
}>()
</script>