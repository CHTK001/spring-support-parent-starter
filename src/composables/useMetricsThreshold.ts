import { ref, computed } from 'vue'

/**
 * 指标阈值配置
 */
export interface ThresholdLevel {
  normal: number
  warning: number
  critical: number
}

/**
 * 阈值配置类型
 */
export interface MetricsThresholds {
  cpu: ThresholdLevel
  memory: ThresholdLevel
  disk: ThresholdLevel
  temperature: ThresholdLevel
  network: ThresholdLevel
}

/**
 * 颜色级别
 */
export type ColorLevel = 'normal' | 'warning' | 'critical'

/**
 * 默认阈值配置
 */
const defaultThresholds: MetricsThresholds = {
  cpu: { normal: 50, warning: 80, critical: 90 },
  memory: { normal: 60, warning: 80, critical: 90 },
  disk: { normal: 70, warning: 85, critical: 95 },
  temperature: { normal: 50, warning: 70, critical: 85 },
  network: { normal: 60, warning: 80, critical: 90 }
}

/**
 * 颜色映射
 */
const colorMap = {
  normal: '#67c23a',   // 绿色
  warning: '#e6a23c',  // 黄色
  critical: '#f56c6c'  // 红色
}

/**
 * 指标阈值管理组合式函数
 */
export function useMetricsThreshold() {
  const thresholds = ref<MetricsThresholds>(defaultThresholds)

  /**
   * 获取指标的颜色级别
   */
  const getColorLevel = (metricType: keyof MetricsThresholds, value: number): ColorLevel => {
    const threshold = thresholds.value[metricType]
    if (!threshold) return 'normal'
    
    if (value >= threshold.critical) return 'critical'
    if (value >= threshold.warning) return 'warning'
    return 'normal'
  }

  /**
   * 获取指标的颜色代码
   */
  const getColorCode = (metricType: keyof MetricsThresholds, value: number): string => {
    const level = getColorLevel(metricType, value)
    return colorMap[level]
  }

  /**
   * 获取进度条颜色配置（支持渐变）
   */
  const getProgressColor = (metricType: keyof MetricsThresholds, value?: number) => {
    const threshold = thresholds.value[metricType]
    if (!threshold) return colorMap.normal

    // 如果提供了具体值，返回对应颜色
    if (value !== undefined) {
      return getColorCode(metricType, value)
    }

    // 返回渐变色配置数组
    return [
      { color: colorMap.normal, percentage: threshold.normal },
      { color: colorMap.warning, percentage: threshold.warning },
      { color: colorMap.critical, percentage: 100 }
    ]
  }

  /**
   * 获取Element Plus进度条颜色函数
   */
  const getElementProgressColor = (metricType: keyof MetricsThresholds) => {
    const threshold = thresholds.value[metricType]
    return (percentage: number) => {
      if (percentage >= threshold.critical) return colorMap.critical
      if (percentage >= threshold.warning) return colorMap.warning
      return colorMap.normal
    }
  }

  /**
   * 检查是否为警告级别
   */
  const isWarningLevel = (metricType: keyof MetricsThresholds, value: number): boolean => {
    const threshold = thresholds.value[metricType]
    return value >= threshold.warning && value < threshold.critical
  }

  /**
   * 检查是否为危险级别
   */
  const isCriticalLevel = (metricType: keyof MetricsThresholds, value: number): boolean => {
    const threshold = thresholds.value[metricType]
    return value >= threshold.critical
  }

  /**
   * 获取阈值描述文本
   */
  const getThresholdText = (metricType: keyof MetricsThresholds, value: number): string => {
    const level = getColorLevel(metricType, value)
    const texts = {
      normal: '正常',
      warning: '警告',
      critical: '危险'
    }
    return texts[level]
  }

  /**
   * 获取阈值图标
   */
  const getThresholdIcon = (metricType: keyof MetricsThresholds, value: number): string => {
    const level = getColorLevel(metricType, value)
    const icons = {
      normal: 'ri:checkbox-circle-line',
      warning: 'ri:error-warning-line',
      critical: 'ri:close-circle-line'
    }
    return icons[level]
  }

  /**
   * 更新阈值配置
   */
  const updateThreshold = (metricType: keyof MetricsThresholds, threshold: ThresholdLevel) => {
    thresholds.value[metricType] = { ...threshold }
  }

  /**
   * 重置为默认阈值
   */
  const resetThresholds = () => {
    thresholds.value = { ...defaultThresholds }
  }

  /**
   * 获取所有阈值配置
   */
  const getAllThresholds = computed(() => thresholds.value)

  /**
   * 获取颜色映射
   */
  const getColorMap = computed(() => colorMap)

  /**
   * 批量获取多个指标的颜色信息
   */
  const getBatchColors = (metrics: Record<string, number>) => {
    const result: Record<string, { colorCode: string; colorLevel: ColorLevel; text: string; icon: string }> = {}
    
    for (const [metricType, value] of Object.entries(metrics)) {
      if (metricType in thresholds.value) {
        const key = metricType as keyof MetricsThresholds
        result[metricType] = {
          colorCode: getColorCode(key, value),
          colorLevel: getColorLevel(key, value),
          text: getThresholdText(key, value),
          icon: getThresholdIcon(key, value)
        }
      }
    }
    
    return result
  }

  return {
    thresholds: getAllThresholds,
    colorMap: getColorMap,
    getColorLevel,
    getColorCode,
    getProgressColor,
    getElementProgressColor,
    isWarningLevel,
    isCriticalLevel,
    getThresholdText,
    getThresholdIcon,
    updateThreshold,
    resetThresholds,
    getBatchColors
  }
}

/**
 * 格式化字节数
 */
export function formatBytes(bytes: number | undefined, decimals = 2): string {
  if (!bytes || bytes === 0) return '0 B'
  
  const k = 1024
  const dm = decimals < 0 ? 0 : decimals
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i]
}

/**
 * 格式化网络速率
 */
export function formatNetworkSpeed(bytesPerSecond: number | undefined): string {
  if (!bytesPerSecond || bytesPerSecond === 0) return '0 B/s'
  
  const k = 1024
  const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  
  const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k))
  
  return parseFloat((bytesPerSecond / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 格式化数字
 */
export function formatNumber(num: number | undefined): string {
  if (!num && num !== 0) return '0'
  return num.toLocaleString()
}

/**
 * 格式化运行时间
 */
export function formatUptime(seconds: number | undefined): string {
  if (!seconds) return 'N/A'
  
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  
  if (days > 0) {
    return `${days}天 ${hours}小时 ${minutes}分钟`
  } else if (hours > 0) {
    return `${hours}小时 ${minutes}分钟`
  } else {
    return `${minutes}分钟`
  }
}

/**
 * 格式化时间
 */
export function formatTime(time: string | Date | undefined): string {
  if (!time) return 'N/A'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
