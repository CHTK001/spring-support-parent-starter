/**
 * 设备类别常量与工具
 *
 * @author CH
 * @since 4.0.0.42
 */

import type { DeviceCategory } from '../types/device'

/**
 * 大类中文映射
 */
export const CATEGORY_LABELS: Record<DeviceCategory, string> = {
  camera: '摄像头',
  accessControl: '门禁',
  alarm: '报警',
  intercom: '对讲'
}

/**
 * 大类小类下拉选项映射
 */
export const SUB_CATEGORY_OPTIONS: Record<DeviceCategory, { label: string; value: string }[]> = {
  camera: [
    { label: '枪机', value: 'bullet' },
    { label: '球机', value: 'dome' },
    { label: '云台', value: 'ptz' },
    { label: '半球', value: 'hemisphere' }
  ],
  accessControl: [
    { label: '读卡器', value: 'cardReader' },
    { label: '指纹', value: 'fingerprint' },
    { label: '人脸', value: 'face' },
    { label: '密码键盘', value: 'keypad' }
  ],
  alarm: [
    { label: '烟感', value: 'smoke' },
    { label: '红外', value: 'infrared' },
    { label: '紧急按钮', value: 'panicButton' }
  ],
  intercom: [
    { label: '门口机', value: 'doorStation' },
    { label: '室内机', value: 'indoorStation' }
  ]
}

/**
 * 获取大类显示文本
 *
 * @param category 大类
 * @returns 中文标签
 */
export function getCategoryLabel(category: string): string {
  return CATEGORY_LABELS[category as DeviceCategory] || category
}

/**
 * 获取指定大类的小类选项
 *
 * @param category 大类
 * @returns 小类选项数组
 */
export function getSubCategoryOptions(category: string): { label: string; value: string }[] {
  return SUB_CATEGORY_OPTIONS[category as DeviceCategory] || []
}