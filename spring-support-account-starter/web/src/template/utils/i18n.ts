/**
 * 模板库 UI 字典（语言 / 模板类型 / 状态对应的显示文本与标签）
 *
 * @author CH
 * @since 4.0.0.42
 */

/** 模板语言 */
export const tplLanguageLabels: Record<string, string> = {
  html: 'HTML',
  vue: 'Vue',
  jsx: 'JSX',
  json: 'JSON'
}

/** 模板类型 */
export const tplTypeLabels: Record<string, string> = {
  document: '单据',
  sms: '短信',
  email: '邮件',
  label: '标签',
  other: '其他'
}

/** 模板状态 */
export const tplStatusLabels: Record<string, string> = {
  draft: '草稿',
  online: '已发布',
  offline: '未发布'
}

/** 模板状态 -> Element Plus tag 类型 */
export const tplStatusTagTypes: Record<string, string> = {
  draft: 'info',
  online: 'success',
  offline: 'warning'
}

/** 模板来源 */
export const tplSourceLabels: Record<string, string> = {
  hiprint: '绘制',
  message: '消息',
  paper: '组卷'
}

/** 模板来源显示文本 */
export function sourceLabel(source: string): string {
  return tplSourceLabels[source] ?? source
}

/** 模板状态显示文本 */
export function statusLabel(status: string): string {
  return tplStatusLabels[status] ?? status
}

/** 状态元数据（tag 类型 + 文本） */
export function statusMeta(status: string): { type: string; label: string } {
  return {
    type: tplStatusTagTypes[status] ?? 'info',
    label: statusLabel(status)
  }
}

/** 语言显示文本 */
export function languageLabel(language: string): string {
  return tplLanguageLabels[language] ?? language
}

/** 类型显示文本 */
export function typeLabel(type: string): string {
  return tplTypeLabels[type] ?? type
}

/** 调用日志操作类型 */
export const tplActionLabels: Record<string, string> = {
  preview: '预览',
  design: '设计',
  print: '打印',
  sync: '同步'
}

/** 接入方 */
export const tplVendorLabels: Record<string, string> = {
  aliyun: '阿里云',
  tencent: '腾讯云',
  self: '自建'
}

/** 接入方显示文本 */
export function vendorLabel(vendor: string): string {
  return tplVendorLabels[vendor] ?? vendor
}

/** 调用日志操作类型显示文本 */
export function actionLabel(action: string): string {
  return tplActionLabels[action] ?? action
}
