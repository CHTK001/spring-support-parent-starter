/**
 * 设备类型定义
 *
 * @author CH
 * @since 4.0.0.42
 */

/**
 * 设备大类枚举
 */
export type DeviceCategory = 'camera' | 'accessControl' | 'alarm' | 'intercom'

/**
 * 设备信息实体
 */
export interface DeviceInfo {
  deviceInfoId: string
  deviceInfoOrgId: string
  deviceInfoName: string
  deviceInfoVendor: string
  deviceInfoModel: string
  deviceInfoCategory: DeviceCategory
  deviceInfoSubCategory: string
  deviceInfoIp: string
  deviceInfoPort: number
  deviceInfoUsername?: string
  deviceInfoOnline: boolean
  deviceInfoSerial?: string
  deviceInfoFirmware?: string
  deviceInfoChannelCount?: number
  deviceInfoLatitude?: number
  deviceInfoLongitude?: number
  deviceInfoAddress?: string
  deviceInfoOfflineThreshold?: number
  deviceInfoCreatedAt?: string
  channels?: DeviceChannel[]
  doors?: DeviceDoor[]
}

/**
 * 设备门禁实体
 */
export interface DeviceDoor {
  deviceDoorNo: number
  deviceDoorName: string
  deviceDoorStatus: string
}

/**
 * 组织节点
 */
export interface OrgNode {
  deviceOrganizationId: string
  deviceOrganizationName: string
  children?: OrgNode[]
}

/**
 * 时间轴条目
 */
export interface TimelineItem {
  id: string
  time: string
  type: 'primary' | 'success' | 'warning' | 'danger'
  content: string
}

/**
 * 预置位
 */
export interface Preset {
  devicePresetNo: number
  devicePresetName: string
}

/**
 * 门禁事件
 */
export interface AccessEvent {
  time: string
  doorName: string
  eventType: string
  cardNo: string
}

/**
 * 设备事件
 */
export interface DeviceEvent {
  deviceEventRecordId?: string
  deviceEventRecordDeviceId?: string
  deviceEventRecordChannelNo?: number
  deviceEventRecordEventType?: string
  deviceEventRecordDetail?: string
  deviceEventRecordOccurredAt?: string
}

/**
 * 视频通道
 */
export interface VideoChannel {
  id: number
  name?: string
}

/**
 * 设备通道实体（对应后端 DeviceChannel）
 */
export interface DeviceChannel {
  deviceChannelId: string
  deviceChannelDeviceId: string
  deviceChannelNo: number
  deviceChannelName: string
  deviceChannelType: string
  deviceChannelStatus: string
  deviceChannelSort: number
}

/**
 * 提供商配置
 */
export interface ProviderConfig {
  vendor: string
  defaultOfflineThreshold: number
}