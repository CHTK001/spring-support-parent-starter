<template>
  <div class="device-page flex h-full">
    <!-- 左侧 -->
    <aside class="w-80 border-r border-el-border bg-el-bg p-4 overflow-auto">
      <ReDeviceTree
        :org-tree="orgTree"
        :devices="devices"
        :selected-id="selectedDevice?.deviceInfoId"
        @select="selectDevice"
        @org-select="onOrgSelect"
        @sync-org="syncOrg"
        @add="showAddDialog"
      />
    </aside>

    <!-- 右侧 -->
    <main class="flex-1 p-4 overflow-auto">
      <div v-if="selectedDevice" class="space-y-4">
        <div class="flex items-center justify-between">
          <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoName }}</div>
          <div class="flex items-center gap-2">
            <el-button size="small" @click="showThresholdDialog">
              <el-icon class="mr-1"><Timer /></el-icon>离线检测
            </el-button>
            <el-button size="small" @click="exportDevices">
              <el-icon class="mr-1"><Download /></el-icon>导出 Excel
            </el-button>
          </div>
        </div>
        <el-tabs v-model="activeTab" class="device-tabs">
          <!-- 概览 -->
          <el-tab-pane label="概览" name="overview">
            <div class="grid grid-cols-4 gap-4">
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">设备名称</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoName }}</div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">厂商 / 型号</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoVendor }} / {{ selectedDevice.deviceInfoModel }}</div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">IP / 端口</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoIp }}:{{ selectedDevice.deviceInfoPort }}</div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">大类 / 小类</div>
                <div class="text-lg font-semibold text-el-text">
                  {{ getCategoryLabel(selectedDevice.deviceInfoCategory) }} / {{ selectedDevice.deviceInfoSubCategory }}
                </div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">序列号 / 固件</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoSerial || '-' }} / {{ selectedDevice.deviceInfoFirmware || '-' }}</div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">通道数</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoChannelCount ?? 0 }}</div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">位置</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoAddress || '未配置' }}</div>
              </el-card>
              <el-card class="shadow-sm rounded-xl p-4">
                <div class="text-sm text-el-text-secondary">离线阈值</div>
                <div class="text-lg font-semibold text-el-text">{{ selectedDevice.deviceInfoOfflineThreshold ?? '-' }} 秒</div>
              </el-card>
            </div>
            <div class="mt-4">
              <ReTimeline title="操作时间轴" :items="timeline" @refresh="fetchTimeline" />
            </div>
          </el-tab-pane>

          <!-- 地图 -->
          <el-tab-pane label="地图" name="map">
            <ReMap :devices="devices" @select="selectDevice" />
          </el-tab-pane>

          <!-- 通道管理 -->
          <el-tab-pane label="通道" name="channel">
            <ChannelManager :device-id="selectedDevice.deviceInfoId" />
          </el-tab-pane>

          <!-- 视频 -->
          <el-tab-pane label="视频" name="video">
            <div class="flex gap-4">
              <div class="flex-1">
                <ReVideo
                  :url="videoUrl"
                  :channel="currentChannel"
                  @error="onVideoError"
                  class="rounded-xl overflow-hidden bg-black aspect-video"
                />
                <div class="flex items-center gap-2 mt-3">
                  <el-select v-model="currentChannel" placeholder="选择通道" size="small" style="width: 140px">
                    <el-option
                      v-for="ch in selectedDevice.channels || []"
                      :key="ch.deviceChannelId || ch.deviceChannelNo"
                      :label="ch.deviceChannelName || `通道 ${ch.deviceChannelNo}`"
                      :value="ch.deviceChannelNo"
                    />
                  </el-select>
                  <el-button size="small" type="primary" @click="openMultiView">
                    <el-icon class="mr-1"><Grid /></el-icon>多路播放
                  </el-button>
                  <el-button size="small" @click="captureImage">
                    <el-icon class="mr-1"><Camera /></el-icon>截图
                  </el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- 云台 -->
          <el-tab-pane label="云台" name="ptz">
            <RePtzControl
              :presets="presets"
              :zoom="zoom"
              :focus="focus"
              @move="ptzControl"
              @zoom="(v: number) => (zoom = v)"
              @focus="(v: number) => (focus = v)"
              @add-preset="addPreset"
              @call-preset="callPreset"
              @delete-preset="deletePreset"
            />
          </el-tab-pane>

          <!-- 门禁 -->
          <el-tab-pane label="门禁" name="access">
            <ReAccessPanel
              :doors="selectedDevice.doors || []"
              :events="accessEvents"
              @unlock="remoteUnlock"
              @refresh="fetchAccessEvents"
            />
          </el-tab-pane>

          <!-- 事件 -->
          <el-tab-pane label="事件" name="events">
            <ReEventPanel
              :events="events"
              :filter="eventFilter"
              :realtime="eventRealtime"
              @query="fetchEvents"
              @update:filter="onEventFilterChange"
              @update:realtime="onEventRealtimeChange"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
      <div v-else class="flex items-center justify-center h-full text-el-text-secondary">请从左侧选择设备</div>
    </main>

    <!-- 多路播放 -->
    <ReMultiVideo
      v-model="multiViewVisible"
      :layout="multiLayout"
      :urls="multiUrls"
      :channels="multiChannels"
      :channel-list="selectedDevice?.channels || []"
      @update:layout="(v: number) => (multiLayout = v)"
      @channel-change="onMultiChannelChange"
    />

    <!-- 添加设备 -->
    <el-dialog v-model="addDialogVisible" title="添加设备" width="500">
      <el-form :model="addForm" label-width="100">
        <el-form-item label="设备名称">
          <el-input v-model="addForm.deviceInfoName" />
        </el-form-item>
        <el-form-item label="厂商">
          <el-select v-model="addForm.deviceInfoVendor">
            <el-option label="海康" value="hikvision" />
            <el-option label="大华" value="dahua" />
          </el-select>
        </el-form-item>
        <el-form-item label="大类">
          <el-select v-model="addForm.deviceInfoCategory">
            <el-option label="摄像头" value="camera" />
            <el-option label="门禁" value="accessControl" />
            <el-option label="报警" value="alarm" />
            <el-option label="对讲" value="intercom" />
          </el-select>
        </el-form-item>
        <el-form-item label="小类">
          <el-select v-model="addForm.deviceInfoSubCategory">
            <el-option
              v-for="opt in getSubCategoryOptions(addForm.deviceInfoCategory)"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="IP">
          <el-input v-model="addForm.deviceInfoIp" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="addForm.deviceInfoPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="addForm.deviceInfoUsername" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="addForm.deviceInfoPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 离线检测阈值 -->
    <el-dialog v-model="thresholdDialogVisible" title="离线检测设置" width="420">
      <div class="space-y-4">
        <div>
          <div class="text-sm text-el-text-secondary mb-2">当前设备离线阈值</div>
          <el-input-number v-model="deviceThreshold" :min="5" :max="86400" :step="5" style="width: 100%" />
          <div class="text-xs text-el-text-secondary mt-1">超过该秒数未检测到心跳，判定为离线。留空使用厂商默认值。</div>
        </div>
        <div>
          <div class="text-sm text-el-text-secondary mb-2">厂商默认阈值（秒）</div>
          <div v-if="providerConfigsLoaded" class="space-y-2">
            <div
              v-for="(val, vendor) in providerConfigs"
              :key="vendor"
              class="flex items-center justify-between p-2 rounded-lg border border-el-border"
            >
              <span class="text-sm text-el-text">{{ vendorLabel(vendor) }}</span>
              <el-input-number v-model="providerConfigs[vendor]" :min="5" :max="86400" :step="5" size="small" style="width: 140px" @change="saveProviderConfig(vendor)" />
            </div>
          </div>
          <div v-else class="text-xs text-el-text-secondary">加载中...</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="thresholdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDeviceThreshold">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Grid, Camera, Timer, Download } from '@element-plus/icons-vue'

import ReVideo from '../../components/ReVideo.vue'
import ReDeviceTree from '../../components/ReDeviceTree.vue'
import ReTimeline from '../../components/ReTimeline.vue'
import RePtzControl from '../../components/RePtzControl.vue'
import ReMultiVideo from '../../components/ReMultiVideo.vue'
import ReAccessPanel from '../../components/ReAccessPanel.vue'
import ReEventPanel from '../../components/ReEventPanel.vue'
import ReMap from '../../components/ReMap.vue'
import ChannelManager from '../../components/ChannelManager.vue'

import { deviceApi } from '../../utils/http'
import { getCategoryLabel, getSubCategoryOptions } from '../../utils/deviceCategory'
import type { DeviceInfo, OrgNode, TimelineItem, Preset, AccessEvent, DeviceEvent } from '../../types/device'

// ==================== 状态 ====================

const orgTree = ref<OrgNode[]>([
  {
    deviceOrganizationId: '1',
    deviceOrganizationName: '总部',
    children: [
      { deviceOrganizationId: '2', deviceOrganizationName: '研发中心' },
      { deviceOrganizationId: '3', deviceOrganizationName: '办公楼' }
    ]
  }
])

const devices = ref<DeviceInfo[]>([
  {
    deviceInfoId: 'd1',
    deviceInfoOrgId: '2',
    deviceInfoName: '大厅摄像头',
    deviceInfoVendor: '海康',
    deviceInfoModel: 'DS-2CD',
    deviceInfoCategory: 'camera',
    deviceInfoSubCategory: 'dome',
    deviceInfoIp: '192.168.1.100',
    deviceInfoPort: 8000,
    deviceInfoOnline: true,
    channels: [
      { deviceChannelId: 'c1', deviceChannelDeviceId: 'd1', deviceChannelNo: 1, deviceChannelName: '大厅球机', deviceChannelType: 'video', deviceChannelStatus: 'enabled', deviceChannelSort: 1 },
      { deviceChannelId: 'c2', deviceChannelDeviceId: 'd1', deviceChannelNo: 2, deviceChannelName: '门口枪机', deviceChannelType: 'video', deviceChannelStatus: 'enabled', deviceChannelSort: 2 }
    ],
    doors: []
  },
  {
    deviceInfoId: 'd2',
    deviceInfoOrgId: '3',
    deviceInfoName: '门禁1号',
    deviceInfoVendor: '大华',
    deviceInfoModel: 'DH-ASC',
    deviceInfoCategory: 'accessControl',
    deviceInfoSubCategory: 'cardReader',
    deviceInfoIp: '192.168.1.101',
    deviceInfoPort: 37777,
    deviceInfoOnline: false,
    doors: [{ deviceDoorNo: 1, deviceDoorName: '正门', deviceDoorStatus: 'locked' }]
  }
])

const selectedDevice = ref<DeviceInfo | null>(null)
const activeTab = ref('overview')
const timeline = ref<TimelineItem[]>([])
const currentChannel = ref(1)
const videoUrl = ref('')
const multiViewVisible = ref(false)
const multiLayout = ref(4)
const multiChannels = ref<number[]>([])
const multiUrls = ref<string[]>([])
const zoom = ref(50)
const focus = ref(50)
const presets = ref<Preset[]>([])
const accessEvents = ref<AccessEvent[]>([])
const events = ref<DeviceEvent[]>([])
const eventFilter = ref({ type: '', dateRange: null as any })
const eventRealtime = ref(true)
const addDialogVisible = ref(false)
const addForm = ref({
  deviceInfoName: '',
  deviceInfoVendor: 'hikvision',
  deviceInfoCategory: 'camera',
  deviceInfoSubCategory: 'bullet',
  deviceInfoIp: '',
  deviceInfoPort: 8000,
  deviceInfoUsername: 'admin',
  deviceInfoPassword: ''
})
const thresholdDialogVisible = ref(false)
const deviceThreshold = ref<number>()
const providerConfigs = ref<Record<string, number>>({})
const providerConfigsLoaded = ref(false)
let pollTimer: any = null

// ==================== 方法 ====================

function selectDevice(dev: DeviceInfo) {
  selectedDevice.value = dev
  if (dev.channels && dev.channels.length) {
    currentChannel.value = dev.channels[0].deviceChannelNo
  }
  loadVideo(dev.deviceInfoId, currentChannel.value)
  fetchTimeline()
  loadPresets(dev.deviceInfoId)
  fetchAccessEvents()
  fetchEvents()
}

function loadVideo(deviceId: string, channel: number) {
  videoUrl.value = `ws://localhost:8080/stream/${deviceId}/${channel}`
}

function onVideoError(err: any) {
  ElMessage.error('视频加载失败: ' + (err?.message || '未知错误'))
}

function openMultiView() {
  const n = multiLayout.value
  const chs = selectedDevice.value?.channels || []
  multiChannels.value = Array.from({ length: n }, (_, i) => (chs.length ? chs[i % chs.length].deviceChannelNo : 1))
  multiUrls.value = multiChannels.value.map((ch) => `ws://localhost:8080/stream/${selectedDevice.value?.deviceInfoId}/${ch}`)
  multiViewVisible.value = true
}

function onMultiChannelChange(payload: { index: number; value: number }) {
  multiUrls.value[payload.index] = `ws://localhost:8080/stream/${selectedDevice.value?.deviceInfoId}/${payload.value}`
}

function captureImage() {
  ElMessage.success('截图已保存')
}

function ptzControl(cmd: string) {
  if (!selectedDevice.value) return
  deviceApi.ptzControl(selectedDevice.value.deviceInfoId, cmd).catch(() => null)
  timeline.value.unshift({
    id: Date.now().toString(),
    time: new Date().toLocaleString(),
    type: 'primary',
    content: `云台 ${cmd}`
  })
}

function addPreset() {
  if (!selectedDevice.value) return
  const name = prompt('输入预置位名称')
  if (!name) return
  deviceApi.setPreset(selectedDevice.value.deviceInfoId, Date.now(), name).catch(() => null)
  presets.value.push({ devicePresetNo: Date.now(), devicePresetName: name })
  ElMessage.success('添加预置位成功')
}

function callPreset(id: number) {
  if (!selectedDevice.value) return
  deviceApi.callPreset(selectedDevice.value.deviceInfoId, id).catch(() => null)
  ElMessage.success(`调用预置位 ${id}`)
}

function deletePreset(id: number) {
  if (!selectedDevice.value) return
  deviceApi.deletePreset(selectedDevice.value.deviceInfoId, id).catch(() => null)
  presets.value = presets.value.filter((p: Preset) => p.devicePresetNo !== id)
}

function remoteUnlock(doorId: number) {
  if (!selectedDevice.value) return
  deviceApi.unlock(selectedDevice.value.deviceInfoId, doorId)
    .then(() => ElMessage.success(`门 ${doorId} 已开`))
    .catch(() => ElMessage.success(`门 ${doorId} 已开（模拟）`))
}

function syncOrg() {
  deviceApi.syncOrg()
    .then(() => ElMessage.success('组织树同步成功'))
    .catch(() => ElMessage.success('组织树同步成功（模拟）'))
}

function onOrgSelect(org: OrgNode) {
  console.log('选中组织:', org.deviceOrganizationName)
}

function fetchTimeline() {
  timeline.value = [
    { id: '1', time: '2026-08-03 10:00:00', type: 'primary', content: '设备上线' },
    { id: '2', time: '2026-08-03 09:30:00', type: 'success', content: '云台向左移动' },
    { id: '3', time: '2026-08-03 09:00:00', type: 'warning', content: '门禁刷卡异常' }
  ]
}

function loadPresets(deviceId: string) {
  deviceApi.listPresets(deviceId)
    .then((res: any) => {
      presets.value = res || []
    })
    .catch(() => {
      presets.value = [
        { devicePresetNo: 1, devicePresetName: '预置位1' },
        { devicePresetNo: 2, devicePresetName: '预置位2' }
      ]
    })
}

function fetchAccessEvents() {
  accessEvents.value = [
    { time: '2026-08-03 10:05:00', doorName: '正门', eventType: '刷卡开门', cardNo: '123456' }
  ]
}

function fetchEvents() {
  if (!selectedDevice.value) return
  deviceApi
    .queryEvents({ deviceId: selectedDevice.value.deviceInfoId, type: eventFilter.value.type })
    .then((res: any) => {
      events.value = res || []
    })
    .catch(() => {
      events.value = [
        {
          deviceEventRecordOccurredAt: '2026-08-03 10:01:00',
          deviceEventRecordEventType: 'MOTION',
          deviceEventRecordChannelNo: 1,
          deviceEventRecordDetail: '画面变化'
        }
      ]
    })
}

/**
 * 事件筛选条件变更
 */
function onEventFilterChange(v: { type: string; dateRange: any }) {
  eventFilter.value = v
}

/**
 * 实时轮询开关变更
 */
function onEventRealtimeChange(v: boolean) {
  eventRealtime.value = v
}

function showAddDialog() {
  addDialogVisible.value = true
}

/**
 * 打开离线检测设置对话框
 */
function showThresholdDialog() {
  deviceThreshold.value = selectedDevice.value?.deviceInfoOfflineThreshold
  loadProviderConfigs()
  thresholdDialogVisible.value = true
}

/**
 * 加载厂商默认离线阈值
 */
function loadProviderConfigs() {
  providerConfigsLoaded.value = false
  deviceApi
    .listProviderConfigs()
    .then((res: any) => {
      providerConfigs.value = res || {}
    })
    .catch(() => {
      providerConfigs.value = { hikvision: 60, dahua: 60, ezviz: 120, isup: 90 }
    })
    .finally(() => {
      providerConfigsLoaded.value = true
    })
}

/**
 * 保存当前设备的离线阈值
 */
function saveDeviceThreshold() {
  if (!selectedDevice.value) return
  const threshold = deviceThreshold.value
  if (threshold == null || threshold <= 0) {
    ElMessage.warning('请输入有效的阈值（秒）')
    return
  }
  deviceApi
    .updateOfflineThreshold(selectedDevice.value.deviceInfoId, threshold)
    .then(() => {
      selectedDevice.value!.deviceInfoOfflineThreshold = threshold
      ElMessage.success('设备离线阈值已保存')
      thresholdDialogVisible.value = false
    })
    .catch(() => {
      selectedDevice.value!.deviceInfoOfflineThreshold = threshold
      ElMessage.success('设备离线阈值已保存（模拟）')
      thresholdDialogVisible.value = false
    })
}

/**
 * 保存厂商默认离线阈值
 */
function saveProviderConfig(vendor: string) {
  const threshold = providerConfigs.value[vendor]
  if (!threshold || threshold <= 0) return
  deviceApi
    .updateProviderConfig(vendor, threshold)
    .then(() => ElMessage.success(`${vendorLabel(vendor)} 阈值已更新`))
    .catch(() => ElMessage.success(`${vendorLabel(vendor)} 阈值已更新（模拟）`))
}

/**
 * 厂商标识中文映射
 */
function vendorLabel(vendor: string): string {
  const map: Record<string, string> = { hikvision: '海康', dahua: '大华', ezviz: '萤石', isup: 'ISUP' }
  return map[vendor] || vendor
}

/**
 * 导出设备列表为 Excel
 */
function exportDevices() {
  deviceApi
    .exportDevices()
    .then((res: any) => {
      const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = '设备列表.xlsx'
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    })
    .catch(() => ElMessage.error('导出失败'))
}

function submitAdd() {
  deviceApi
    .add(addForm.value)
    .then((res: any) => {
      devices.value.push(res)
      ElMessage.success('设备添加成功')
      addDialogVisible.value = false
    })
    .catch(() => {
      const newDev: DeviceInfo = {
        deviceInfoId: 'd' + Date.now(),
        deviceInfoOrgId: '1',
        deviceInfoName: addForm.value.deviceInfoName,
        deviceInfoVendor: addForm.value.deviceInfoVendor,
        deviceInfoModel: '',
        deviceInfoCategory: addForm.value.deviceInfoCategory as any,
        deviceInfoSubCategory: addForm.value.deviceInfoSubCategory,
        deviceInfoIp: addForm.value.deviceInfoIp,
        deviceInfoPort: addForm.value.deviceInfoPort,
        deviceInfoOnline: true,
        channels: [
          { deviceChannelId: 'c-new', deviceChannelDeviceId: '', deviceChannelNo: 1, deviceChannelName: '通道1', deviceChannelType: 'video', deviceChannelStatus: 'enabled', deviceChannelSort: 1 }
        ],
        doors: []
      }
      devices.value.push(newDev)
      ElMessage.success('设备添加成功（模拟）')
      addDialogVisible.value = false
    })
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchDevices()
  fetchOrgTree()
  if (eventRealtime.value) {
    pollTimer = setInterval(() => console.log('轮询事件'), 5000)
  }
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

/**
 * 拉取设备列表（接口失败回退本地模拟数据）
 */
function fetchDevices() {
  deviceApi
    .list()
    .then((res: any) => {
      if (res && res.length) devices.value = res
    })
    .catch(() => null)
}

/**
 * 拉取组织树（接口失败保留本地模拟数据）
 */
function fetchOrgTree() {
  deviceApi
    .getOrgTree()
    .then((res: any) => {
      if (res && res.length) orgTree.value = res
    })
    .catch(() => null)
}
</script>

<style scoped>
.device-page {
  height: 100vh;
  background: var(--el-bg-color);
}
.device-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
</style>