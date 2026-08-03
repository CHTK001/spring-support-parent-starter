/**
 * HTTP 工具封装（基于 axios）
 *
 * @author CH
 * @since 4.0.0.42
 */

import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'

/**
 * 后端基础地址
 */
const BASE_URL = '/api'

/**
 * 统一 axios 实例
 */
const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000
})

/**
 * 请求拦截器
 */
instance.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 响应拦截器
 */
instance.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('[HTTP]', error?.message)
    return Promise.reject(error)
  }
)

/**
 * GET 请求
 *
 * @param url 路径
 * @param config axios 配置
 * @returns 响应数据
 */
export function get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, config) as unknown as Promise<T>
}

/**
 * POST 请求
 *
 * @param url 路径
 * @param data 请求体
 * @param config axios 配置
 * @returns 响应数据
 */
export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>
}

/**
 * PUT 请求
 *
 * @param url 路径
 * @param data 请求体
 * @param config axios 配置
 * @returns 响应数据
 */
export function put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>
}

/**
 * DELETE 请求
 *
 * @param url 路径
 * @param config axios 配置
 * @returns 响应数据
 */
export function del<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>
}

/**
 * 设备模块接口封装
 */
export const deviceApi = {
  list: (orgId?: string) => get('/device/list', { params: { orgId } }),
  listByCategory: (category: string) => get(`/device/category/${category}`),
  get: (id: string) => get(`/device/${id}`),
  add: (info: any) => post('/device/add', info),
  update: (id: string, info: any) => put(`/device/${id}`, info),
  remove: (id: string) => del(`/device/${id}`),
  syncOrg: () => post('/device/org/sync'),
  getOrgTree: (vendor?: string) => get('/device/org/tree', { params: { vendor } }),
  exportDevices: (orgId?: string) => get('/device/export', { params: { orgId }, responseType: 'blob' }),
  getVideoStream: (deviceId: string, channel: number, protocol = 'hls') =>
    get('/device/video/stream', { params: { deviceId, channel, protocol } }),
  getPlaybackUrl: (deviceId: string, channel: number, start: string, end: string) =>
    get('/device/video/playback', { params: { deviceId, channel, start, end } }),
  queryVideoRecords: (deviceId: string, channel: number, start: string, end: string) =>
    get('/device/video/records', { params: { deviceId, channel, start, end } }),
  ptzControl: (deviceId: string, cmd: string, speed = 0.5) =>
    post(`/device/ptz/control?deviceId=${deviceId}&cmd=${cmd}&speed=${speed}`),
  setPreset: (deviceId: string, presetId: number, name: string) =>
    post(`/device/ptz/preset?deviceId=${deviceId}&presetId=${presetId}&name=${encodeURIComponent(name)}`),
  callPreset: (deviceId: string, presetId: number) =>
    post(`/device/ptz/preset/call?deviceId=${deviceId}&presetId=${presetId}`),
  deletePreset: (deviceId: string, presetId: number) =>
    del(`/device/ptz/preset?deviceId=${deviceId}&presetId=${presetId}`),
  listPresets: (deviceId: string) => get(`/device/ptz/preset/list?deviceId=${deviceId}`),
  unlock: (deviceId: string, doorId: number) =>
    post(`/device/access/unlock?deviceId=${deviceId}&doorId=${doorId}`),
  queryAccessRecords: (deviceId: string, start: string, end: string) =>
    get(`/device/access/records?deviceId=${deviceId}&start=${start}&end=${end}`),
  queryEvents: (params: { deviceId: string; type?: string; start?: string; end?: string }) =>
    get('/device/events', { params }),
  // 设备状态
  reconnect: (id: string) => post(`/device/${id}/reconnect`),
  refreshStatus: (id: string) => post(`/device/${id}/refresh-status`),
  testConnection: (info: any) => post('/device/test-connection', info),
  // 通道管理
  addChannel: (channel: any) => post('/device/channel', channel),
  updateChannel: (id: string, channel: any) => put(`/device/channel/${id}`, channel),
  deleteChannel: (id: string) => del(`/device/channel/${id}`),
  getChannel: (id: string) => get(`/device/channel/${id}`),
  listChannels: (deviceId: string) => get('/device/channel/list', { params: { deviceId } }),
  toggleChannelStatus: (id: string, status: string) =>
    put(`/device/channel/${id}/status?status=${status}`),
  // 提供商配置
  listProviderConfigs: () => get('/device/provider-config'),
  updateProviderConfig: (vendor: string, threshold: number) =>
    put(`/device/provider-config/${vendor}?threshold=${threshold}`),
  // 设备离线阈值
  updateOfflineThreshold: (deviceId: string, threshold: number) =>
    put(`/device/${deviceId}/offline-threshold?threshold=${threshold}`),
  // 地图
  getMapDevices: () => get('/device/list')
}