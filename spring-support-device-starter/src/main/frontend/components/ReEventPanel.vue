<template>
  <div class="re-event space-y-4">
    <!-- 筛选栏 -->
    <div class="flex items-center gap-4">
      <el-select :model-value="filter.type" placeholder="事件类型" clearable size="small" style="width: 140px" @update:model-value="(v: string) => emit('update:filter', { ...filter, type: v })">
        <el-option label="移动侦测" value="MOTION" />
        <el-option label="IO报警" value="IO_ALARM" />
        <el-option label="门禁" value="ACCESS" />
        <el-option label="温度" value="TEMP" />
        <el-option label="网络" value="NETWORK" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-date-picker
        :model-value="filter.dateRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        size="small"
        @update:model-value="(v: any) => emit('update:filter', { ...filter, dateRange: v })"
      />
      <el-button size="small" type="primary" @click="emit('query')">查询</el-button>
      <el-switch :model-value="realtime" active-text="实时" inactive-text="轮询" size="small" @update:model-value="(v: boolean) => emit('update:realtime', v)" />
    </div>

    <!-- 表格 -->
    <el-table :data="events" size="small" style="width:100%" max-height="500">
      <el-table-column prop="deviceEventRecordOccurredAt" label="时间" width="180" />
      <el-table-column prop="deviceEventRecordEventType" label="类型" width="120" />
      <el-table-column prop="deviceEventRecordChannelNo" label="通道" width="80" />
      <el-table-column prop="deviceEventRecordDetail" label="详情" />
      <template #empty>
        <el-empty description="无事件" :image-size="60" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { DeviceEvent } from '../types/device'

/**
 * 组件属性
 */
defineProps<{
  events: DeviceEvent[]
  filter: { type: string; dateRange: any }
  realtime: boolean
}>()

/**
 * 事件
 */
const emit = defineEmits<{
  (e: 'query'): void
  (e: 'update:filter', val: { type: string; dateRange: any }): void
  (e: 'update:realtime', val: boolean): void
}>()
</script>