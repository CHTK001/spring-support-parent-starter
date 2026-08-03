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
                  :protocol="videoProtocol"
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
                  <el-select v-model="videoProtocol" placeholder="协议" size="small" style="width: 110px">
                    <el-option label="HLS" value="hls" />
                    <el-option label="FLV" value="flv" />
                    <el-option label="RTSP" value="rtsp" />
                  </el-select>
                  <el-button size="small" type="primary" @click="reloadStream">
                    <el-icon class="mr-1"><Refresh /></el-icon>刷新
                  </el-button>
                  <el-button size="small" type="primary" @click="reconnectDevice">
                    <el-icon class="mr-1"><Refresh /></el-icon>重连
                  </el-button>
                  <el-button size="small" type="primary" @click="openMultiView">
                    <el-icon class="mr-1"><Grid /></el-icon>多路播放
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
    <el-dialog v-model="addDialogVisible" title="添加设备" width="560" append-to-body>
      <el-form :model="addForm" label-width="110">
        <el-form-item label="设备名称">
          <el-input v-model="addForm.deviceInfoName" />
        </el-form-item>
        <el-form-item label="厂商">
          <el-select v-model="addForm.vendor">
            <el-option label="海康" value="hikvision" />
            <el-option label="大华" value="dahua" />
          </el-select>
        </el-form-item>
        <el-form-item label="接入方式">
          <el-radio-group v-model="addForm.authType">
            <el-radio-button label="PLATFORM">平台</el-radio-button>
            <el-radio-button label="SDK">SDK</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="addForm.authType === 'PLATFORM'" label="平台类型">
          <el-select v-model="addForm.platformType">
            <el-option-group label="海康">
              <el-option label="海康云耀" value="cloud-yao" />
              <el-option label="海康云眸" value="cloud-eye" />
              <el-option label="海康安防平台" value="secure-platform" />
            </el-option-group>
            <el-option-group label="大华">
              <el-option label="大华云睿" value="dahua-cloud" />
              <el-option label="大华乐橙" value="lechange" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item v-if="addForm.authType === 'PLATFORM'" label="AppKey">
          <el-input v-model="addForm.appKey" />
        </el-form-item>
        <el-form-item v-if="addForm.authType === 'PLATFORM'" label="AppSecret">
          <el-input v-model="addForm.appSecret" type="password" show-password />
        </el-form-item>
        <el-form-item v-if="addForm.authType === 'PLATFORM'" label="设备序列号">
          <el-input v-model="addForm.deviceSerial" />
        </el-form-item>
        <template v-if="addForm.authType === 'SDK'">
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
        </template>
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
import { Grid, Timer, Download, Refresh } from '@element-plus/icons-vue'

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

// ==================== 状态（默认空，全部由真实接口填充） ====================

const orgTree = ref<OrgNode[]>([])
const devices = ref<DeviceInfo[]>([])
const selectedDevice = ref<DeviceInfo | null>(null)
const activeTab = ref('overview')
const timeline = ref<TimelineItem[]>([])
const currentChannel = ref(1)
const videoUrl = ref('')
const videoProtocol = ref('hls')
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
const thresholdDialogVisible = ref(false)
const deviceThreshold = ref<number>()
const providerConfigs = ref<Record<string, number>>({})
const providerConfigsLoaded = ref(false)
const addForm = ref({
  deviceInfoName: '',
  vendor: 'hikvision',
  authType: 'PLATFORM',
  platformType: 'cloud-eye',
  appKey: '',
  appSecret: '',
  deviceSerial: '',
  deviceInfoCategory: 'camera',
  deviceInfoSubCategory: 'bullet',
  deviceInfoIp: '',
  deviceInfoPort: 8000,
  deviceInfoUsername: 'admin',
  deviceInfoPassword: ''
})
let pollTimer: any = null

// ==================== 方法 ====================

function selectDevice(dev: DeviceInfo) {
  selectedDevice.value = dev
  if (dev.channels && dev.channels.length) {
    currentChannel.value = dev.channels[0].deviceChannelNo
  }
  reloadStream()
  fetchTimeline()
  loadPresets(dev.deviceInfoId)
  fetchAccessEvents()
  fetchEvents()
}

/**
 * 重新加载视频流：从后端获取真实 HLS/FLV URL
 */
function reloadStream() {
  if (!selectedDevice.value) return
  deviceApi
    .getVideoStream(selectedDevice.value.deviceInfoId, currentChannel.value, videoProtocol.value)
    .then((res: any) => {
      videoUrl.value = String(res || '')
      if (!videoUrl.value) {
        ElMessage.warning('后端未返回拉流地址，请检查平台账号配置')
      }
    })
    .catch((err: any) => {
      videoUrl.value = ''
      ElMessage.error('获取视频流失败: ' + (err?.response?.data?.message || err?.message || '未知错误'))
    })
}

/**
 * 重连设备
 */
function reconnectDevice() {
  if (!selectedDevice.value) return
  deviceApi
    .reconnect(selectedDevice.value.deviceInfoId)
    .then(() => {
      ElMessage.success('设备重连成功')
      reloadStream()
    })
    .catch((err: any) => ElMessage.error('设备重连失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function onVideoError(err: any) {
  ElMessage.error('视频加载失败: ' + (err?.message || '未知错误'))
}

function openMultiView() {
  const n = multiLayout.value
  const chs = selectedDevice.value?.channels || []
  const deviceId = selectedDevice.value?.deviceInfoId
  multiChannels.value = Array.from({ length: n }, (_, i) => (chs.length ? chs[i % chs.length].deviceChannelNo : 1))
  Promise.all(
    multiChannels.value.map((ch) =>
      deviceApi
        .getVideoStream(deviceId || '', ch, videoProtocol.value)
        .then((res: any) => String(res || ''))
        .catch(() => ''),
    ),
  ).then((urls) => {
    multiUrls.value = urls
    multiViewVisible.value = true
  })
}

function onMultiChannelChange(payload: { index: number; value: number }) {
  const deviceId = selectedDevice.value?.deviceInfoId
  if (!deviceId) return
  deviceApi
    .getVideoStream(deviceId, payload.value, videoProtocol.value)
    .then((res: any) => {
      multiUrls.value[payload.index] = String(res || '')
    })
    .catch(() => {
      multiUrls.value[payload.index] = ''
    })
}

function ptzControl(cmd: string) {
  if (!selectedDevice.value) return
  deviceApi
    .ptzControl(selectedDevice.value.deviceInfoId, cmd)
    .then(() => {
      timeline.value.unshift({
        id: Date.now().toString(),
        time: new Date().toLocaleString(),
        type: 'primary',
        content: `云台 ${cmd}`
      })
    })
    .catch((err: any) => ElMessage.error('云台控制失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function addPreset() {
  if (!selectedDevice.value) return
  const name = prompt('输入预置位名称')
  if (!name) return
  deviceApi
    .setPreset(selectedDevice.value.deviceInfoId, Date.now(), name)
    .then(() => {
      presets.value.push({ devicePresetNo: Date.now(), devicePresetName: name })
      ElMessage.success('添加预置位成功')
    })
    .catch((err: any) => ElMessage.error('添加预置位失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function callPreset(id: number) {
  if (!selectedDevice.value) return
  deviceApi
    .callPreset(selectedDevice.value.deviceInfoId, id)
    .then(() => ElMessage.success(`调用预置位 ${id}`))
    .catch((err: any) => ElMessage.error('调用预置位失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function deletePreset(id: number) {
  if (!selectedDevice.value) return
  deviceApi
    .deletePreset(selectedDevice.value.deviceInfoId, id)
    .then(() => {
      presets.value = presets.value.filter((p: Preset) => p.devicePresetNo !== id)
      ElMessage.success('删除预置位成功')
    })
    .catch((err: any) => ElMessage.error('删除预置位失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function remoteUnlock(doorId: number) {
  if (!selectedDevice.value) return
  deviceApi
    .unlock(selectedDevice.value.deviceInfoId, doorId)
    .then(() => ElMessage.success(`门 ${doorId} 已开`))
    .catch((err: any) => ElMessage.error('远程开门失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function syncOrg() {
  deviceApi
    .syncOrg()
    .then(() => {
      ElMessage.success('组织树同步成功')
      fetchOrgTree()
    })
    .catch((err: any) => ElMessage.error('组织树同步失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

function onOrgSelect(org: OrgNode) {
  console.log('选中组织:', org.deviceOrganizationName)
}

function fetchTimeline() {
  // 时间轴数据由 DeviceEventService 真实记录驱动（设备事件落库）
  // 此处先用占位接口，预留 /device/timeline 端点
  timeline.value = []
}

function loadPresets(deviceId: string) {
  deviceApi.listPresets(deviceId)
    .then((res: any) => {
      presets.value = res || []
    })
    .catch(() => {
      presets.value = []
    })
}

function fetchAccessEvents() {
  if (!selectedDevice.value) return
  const now = new Date()
  const start = new Date(now.getTime() - 24 * 3600 * 1000)
  const fmt = (d: Date) => d.toISOString().slice(0, 19).replace('T', ' ')
  deviceApi
    .queryAccessRecords(
      selectedDevice.value.deviceInfoId,
      fmt(start),
      fmt(now),
    )
    .then((res: any) => {
      accessEvents.value = (res || []).map((r: any) => ({
        time: r.time || '',
        doorName: r.doorId || '',
        eventType: r.eventType || '',
        cardNo: r.cardNo || '',
        userName: r.userName || ''
      }))
    })
    .catch(() => {
      accessEvents.value = []
    })
}

function fetchEvents() {
  if (!selectedDevice.value) return
  deviceApi
    .queryEvents({ deviceId: selectedDevice.value.deviceInfoId, type: eventFilter.value.type })
    .then((res: any) => {
      events.value = res || []
    })
    .catch(() => {
      events.value = []
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
      providerConfigs.value = {}
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
    .catch((err: any) => ElMessage.error('保存失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
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
    .catch((err: any) => ElMessage.error('更新失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

/**
 * 厂商标识中文映射
 */
function vendorLabel(vendor: string): string {
  const map: Record<string, string> = {
    hikvision: '海康',
    dahua: '大华',
    'cloud-yao': '海康云耀',
    'cloud-eye': '海康云眸',
    'secure-platform': '海康安防',
    'dahua-cloud': '大华云睿',
    lechange: '大华乐橙'
  }
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
    .catch((err: any) => ElMessage.error('导出失败: ' + (err?.response?.data?.message || err?.message || '未知错误')))
}

/**
 * 提交新增设备：直接发往后端，不做任何兜底
 */
function submitAdd() {
  deviceApi
    .add(addForm.value)
    .then((res: any) => {
      if (res && res.deviceInfoId) {
        devices.value.push(res)
      } else {
        fetchDevices()
      }
      ElMessage.success('设备添加成功')
      addDialogVisible.value = false
    })
    .catch((err: any) => {
      ElMessage.error('设备添加失败: ' + (err?.response?.data?.message || err?.message || '未知错误'))
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
 * 拉取设备列表
 */
function fetchDevices() {
  deviceApi
    .list()
    .then((res: any) => {
      devices.value = res || []
    })
    .catch((err: any) => {
      ElMessage.error('加载设备列表失败: ' + (err?.response?.data?.message || err?.message || '未知错误'))
      devices.value = []
    })
}

/**
 * 拉取组织树
 */
function fetchOrgTree() {
  deviceApi
    .getOrgTree()
    .then((res: any) => {
      orgTree.value = res || []
    })
    .catch((err: any) => {
      ElMessage.error('加载组织树失败: ' + (err?.response?.data?.message || err?.message || '未知错误'))
      orgTree.value = []
    })
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
