/**
 * 模板库领域模型
 *
 * 支持多种模板语言（html / vue / jsx / json）与模板类型（单据 / 短信 / 邮件 ...）；
 * 模板以 JSON 描述进行存取，运行时由 vue-plugin-hiprint 的 PrintTemplate 加载、设计与打印。
 *
 * @author CH
 * @since 4.0.0.42
 */

/** 模板语言 */
export type TemplateLanguage = 'html' | 'vue' | 'jsx' | 'json'

/** 模板类型 */
export type TemplateType = 'document' | 'sms' | 'email' | 'label' | 'other'

/** 模板状态 */
export type TemplateStatus = 'draft' | 'online' | 'offline'

/** 模板来源：hiprint（绘制模板）/ message（消息模板）/ paper（组卷模板） */
export type TemplateSource = 'hiprint' | 'message' | 'paper'

/** 接入方：阿里云 / 腾讯云 / 自建 */
export type ProviderVendor = 'aliyun' | 'tencent' | 'self'

/** 提供商状态 */
export type ProviderStatus = 'online' | 'offline'

/**
 * 模板提供商（接入方）
 *
 * 一个提供商代表一条模板来源通道（如「阿里云短信」「自建打印服务」）：
 * 携带调用凭据 AK / SK、接入 endpoint、能承载的模板类型，以及是否允许在本系统里自定义设计模板。
 * 模板通过 {@link TemplateInfo.providerId} 归到某个提供商下。
 *
 * @since 4.0.0.80
 */
export interface ProviderInfo {
  /** 提供商 ID */
  id: string
  /** 提供商名称（如「阿里云短信」） */
  name: string
  /** 接入方 */
  vendor: ProviderVendor
  /** Access Key */
  ak?: string
  /** Secret Key */
  sk?: string
  /** 服务 endpoint（域名或自建服务地址） */
  endpoint: string
  /** 支持的模板类型 */
  templateTypes: TemplateType[]
  /** 是否支持自定义模板设计（开启才允许进入设计器） */
  customDesign: boolean
  /** 状态：online 已启用 / offline 已停用 */
  status: ProviderStatus
  /** 该提供商下的模板数（后端统计） */
  templateCount?: number
  /** 最近一次同步时间 */
  lastSyncAt?: string
  /** 负责人 */
  owner?: string
  /** 说明 */
  remark?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 提供商同步结果 */
export interface ProviderSyncResult {
  /** 新增模板数 */
  added: number
  /** 更新模板数 */
  updated: number
  /** 失败数 */
  failed: number
}

/**
 * 模板详情（按模板 ID 取信息）
 *
 * <p>字段形态对齐 MessagePush 发送一条模板消息所需的东西：
 * {@code sourceCode} → templateId、{@code contentType} → contentType、
 * {@code content} → content、{@code params} → templateParams 的键。
 * 自定义模板没有账号与渠道，这几项为空。</p>
 */
export interface TemplateSourceDetail {
  /** 统一模板库主键 */
  id: string
  /** 模板名称 */
  name: string
  /** 来源：hiprint（自定义）/ message / paper（后两者为固定模板） */
  source: TemplateSource
  /** 是否固定模板（三方同步，内容不可改） */
  fixed: boolean
  /** 来源行 ID */
  sourceId?: string
  /** 三方模板 ID（固定模板才有） */
  sourceCode?: string
  /** 归属账号 / 提供商 ID */
  providerId?: string
  /** 归属账号 / 提供商名称 */
  providerName?: string
  /** 推送渠道标识（MessagePush.create 用） */
  channel?: string
  /** 内容类型 */
  contentType?: string
  /** 模板内容 */
  content?: string
  /** 内容里的占位符参数名 */
  params?: string[]
  /** 备注 */
  remark?: string
}

/** 模板实体（透传后端） */
export interface TemplateInfo {
  /** 模板 ID */
  id: string
  /** 模板名称 */
  name: string
  /** 模板副标题 / 描述 */
  subtitle: string
  /** 模板语言 */
  language: TemplateLanguage
  /** 模板类型 */
  type: TemplateType
  /** 模板状态 */
  status: TemplateStatus
  /** 模板来源：hiprint / message / paper */
  source: TemplateSource
  /** 提供商 ID（模板归属于哪个接入方） */
  providerId?: string
  /** 来源行 ID（消息/组卷模板的主键 ID，hiprint 时缺省） */
  sourceId?: string
  /** 三方模板 ID（固定模板才有，来源侧的模板编码；自定义模板为空） */
  sourceCode?: string
  /** 来源提供方（消息提供商 ID / 组卷归属用户 ID），与 providerId 同理且兼容旧字段 */
  sourceProviderId?: string
  /** 创建者 */
  creator: string
  /** 最后修改者 */
  modifier?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
  /** 模板 JSON 描述（设计器产物） */
  content?: string
  /** 备注 */
  remark?: string
}

/** 模板分页查询条件 */
export interface TemplatePageQuery {
  keyword?: string
  language?: TemplateLanguage
  type?: TemplateType
  status?: TemplateStatus
  source?: TemplateSource
  /** 提供商 ID（按提供商过滤模板） */
  providerId?: string
  page: number
  size: number
}

/** 分页结果 */
export type PageResult<T> = {
  records: T[]
  total: number
  page: number
  size: number
}

/** designer 入参：Proxy 后的模板数据 */
export interface TemplateDesignerPayload {
  /** 模板 ID（新建时为 ''） */
  id: string
  /** 模板基础信息 */
  info: TemplateInfo
  /** 模板设计描述 */
  content: string
  /** 操作模式：'design' / 'preview' / 'print' / 'sandbox' */
  mode: 'design' | 'preview' | 'print' | 'sandbox'
}

/**
 * 打印机连接参数（应用侧 hiprint 调用）
 *
 * "第三方打印"：打印机/打印机服务不在本地，由 {@code address}（IP/域名）、
 * {@code port}（端口）、{@code username}（登录名）、{@code token}（认证） 与
 * 设备名 {@code name} 共同描述；{@code isDefault} 标记该地址是否应被默认选中。
 *
 * @since 4.0.0.60
 * @author CH
 */
export interface PrinterConfig {
  /** 打印机设备名（在 hiprint 中识别的打印机名） */
  name: string
  /** 型号/打印机类型（如 HP LaserJet Pro / 热敏云） */
  type?: string
  /** 连接地址（打印机 IP/域名，或打印服务的地址） */
  address?: string
  /** 连接端口 */
  port?: number
  /** 认证 Token */
  token?: string
  /** 认证用户名 */
  username?: string
  /** 是否默认打印机 */
  isDefault?: boolean
  /** 备注 */
  remark?: string
}

/**
 * 调用日志（模板被预览/设计/打印的记录）
 *
 * 日志由应用侧在用户确认执行操作后统一写入，只记录临时轨迹，不影响业务数据。
 *
 * @since 4.0.0.70
 * @author CH
 */
export interface LogRecord {
  /** 日志 ID */
  id: string
  /** 模板 ID（同步类操作记录提供商 ID） */
  templateId: string
  /** 模板名称（同步类操作记录提供商名称） */
  templateName: string
  /** 操作：preview（预览） / design（设计） / print（打印） / sync（同步） */
  action: 'preview' | 'design' | 'print' | 'sync'
  /** 调用参数（打印时传给打印引擎的参数） */
  parameters: Record<string, any>
  /** 创建时间 */
  time: string
  /** 调用方 IP */
  ip: string
  /** 操作人 */
  operator?: string
}

/** 调用日志分页查询条件 */
export interface TemplateLogQuery {
  /** 模板 ID（精确） */
  templateId?: string
  /** 模板名称（模糊） */
  templateName?: string
  /** 操作类型 */
  action?: 'preview' | 'design' | 'print' | 'sync'
  /** 调用方 IP */
  ip?: string
  /** 页码 */
  page: number
  /** 每页条数 */
  size: number
}
