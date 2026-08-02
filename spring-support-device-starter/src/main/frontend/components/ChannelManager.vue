<template>
  <div class="channel-manager space-y-4">
    <!-- 工具栏 -->
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium text-el-text">通道列表（{{ channels.length }}）</span>
      <el-button size="small" type="primary" @click="openAdd">
        <el-icon class="mr-1"><Plus /></el-icon>添加通道
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="channels" size="small" style="width: 100%" v-loading="loading">
      <el-table-column prop="deviceChannelNo" label="通道号" width="90" />
      <el-table-column prop="deviceChannelName" label="通道名称" min-width="140" />
      <el-table-column prop="deviceChannelType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ channelTypeLabel(row.deviceChannelType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deviceChannelSort" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.deviceChannelStatus === 'enabled'"
            active-text="启用"
            inactive-text="禁用"
            size="small"
            @change="(val: any) => toggleStatus(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="removeChannel(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无通道" :image-size="50" />
      </template>
    </el-table>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑通道' : '添加通道'" width="460" append-to-body>
      <el-form :model="form" label-width="90">
        <el-form-item label="通道号">
          <el-input-number v-model="form.deviceChannelNo" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="通道名称">
          <el-input v-model="form.deviceChannelName" placeholder="例如 大厅球机" />
        </el-form-item>
        <el-form-item label="通道类型">
          <el-select v-model="form.deviceChannelType" class="w-full">
            <el-option label="视频" value="video" />
            <el-option label="门禁" value="door" />
            <el-option label="报警" value="alarm" />
            <el-option label="对讲" value="intercom" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.deviceChannelSort" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { deviceApi } from '../utils/http'
import type { DeviceChannel } from '../types/device'

/**
 * 组件属性
 */
const props = defineProps<{
  deviceId: string
}>()

const channels = ref<DeviceChannel[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const form = ref({
  deviceChannelNo: 1,
  deviceChannelName: '',
  deviceChannelType: 'video',
  deviceChannelSort: 0
})

/**
 * 加载通道列表
 */
function load() {
  if (!props.deviceId) return
  loading.value = true
  deviceApi
    .listChannels(props.deviceId)
    .then((res: any) => {
      channels.value = res || []
    })
    .catch(() => {
      channels.value = []
    })
    .finally(() => {
      loading.value = false
    })
}

/**
 * 打开新增对话框
 */
function openAdd() {
  isEdit.value = false
  editingId.value = ''
  form.value = { deviceChannelNo: channels.value.length + 1, deviceChannelName: '', deviceChannelType: 'video', deviceChannelSort: 0 }
  dialogVisible.value = true
}

/**
 * 打开编辑对话框
 */
function openEdit(row: DeviceChannel) {
  isEdit.value = true
  editingId.value = row.deviceChannelId
  form.value = {
    deviceChannelNo: row.deviceChannelNo,
    deviceChannelName: row.deviceChannelName,
    deviceChannelType: row.deviceChannelType,
    deviceChannelSort: row.deviceChannelSort
  }
  dialogVisible.value = true
}

/**
 * 提交新增/编辑
 */
function submit() {
  const payload = {
    ...form.value,
    deviceChannelDeviceId: props.deviceId,
    deviceChannelStatus: 'enabled'
  }
  const req = isEdit.value ? deviceApi.updateChannel(editingId.value, payload) : deviceApi.addChannel(payload)
  req
    .then(() => {
      ElMessage.success(isEdit.value ? '通道已更新' : '通道已添加')
      dialogVisible.value = false
      load()
    })
    .catch(() => ElMessage.error('操作失败'))
}

/**
 * 启用/禁用通道
 */
function toggleStatus(row: DeviceChannel, val: boolean) {
  const status = val ? 'enabled' : 'disabled'
  deviceApi
    .toggleChannelStatus(row.deviceChannelId, status)
    .then(() => {
      row.deviceChannelStatus = status
      ElMessage.success(val ? '已启用' : '已禁用')
    })
    .catch(() => ElMessage.error('状态更新失败'))
}

/**
 * 删除通道
 */
function removeChannel(row: DeviceChannel) {
  ElMessageBox.confirm(`确认删除通道「${row.deviceChannelName}」？`, '提示', { type: 'warning' })
    .then(() => deviceApi.deleteChannel(row.deviceChannelId))
    .then(() => {
      ElMessage.success('通道已删除')
      load()
    })
    .catch(() => null)
}

/**
 * 通道类型中文映射
 */
function channelTypeLabel(type: string): string {
  const map: Record<string, string> = { video: '视频', door: '门禁', alarm: '报警', intercom: '对讲' }
  return map[type] || type || '-'
}

onMounted(load)
watch(() => props.deviceId, load)
</script>
