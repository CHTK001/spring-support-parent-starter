/**
 * 短信 / 邮件消息模板 API（`/api/channels/templates`）
 *
 * 归「模板管理」域：模板把渠道（短信接码 / 邮件收信）收到的原始消息渲染成可读文本，
 * 正文变量占位写作 `{{变量名}}`，同一种类仅一条默认模板，渠道收信时由后端套用默认
 * 模板补 `display`。后端由 account-starter 的 MessageTemplateController 提供，
 * 是**部署级共享配置**（渠道能力本身不分用户，模板也保持一致）。
 *
 * <p>注意：本组接口在宿主后端进程（如 monitor 的 8082），与模板库 CRUD
 * （`templateApi` 的 `/api/template/page`，走 createModuleHttp('template')）是两套服务，
 * 因此这里**单独造根路径客户端**：createHttp({ baseUrl: '', useAppConfig: false })
 * 挂到 createApp 注入的全局 axios 实例（Bearer / 401 / 统一提示），浏览器直接请求
 * 相对路径 `/api/channels/templates`，由宿主 dev 代理转发到对应后端。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
import { createHttp, unwrapResult } from '@repo/utils'
import type { Http } from '@repo/utils'

/** 兼容统一包装（{ code, data }）与直接返回体（@ApiReturnFormatIgnore 的 ApiResult） */
async function unwrapPayload<T>(request: unknown): Promise<T> {
  return unwrapResult<T>(await (request as Promise<unknown>))
}

/** 根路径客户端：与宿主应用共用全局 axios 管线（鉴权 / 第一方头 / 401） */
const apiHttp: Http = createHttp({ baseUrl: '', useAppConfig: false })

/**
 * 短信 / 邮件消息模板。
 */
export interface MessageTemplate {
  templateId?: string
  /** 模板种类：SMS / EMAIL */
  templateKind: 'SMS' | 'EMAIL'
  templateName: string
  /** 稳定编码，同种类内唯一 */
  templateCode: string
  /** 邮件主题模板（仅 EMAIL） */
  templateSubject?: string | null
  templateContent: string
  /** 模板引用的变量名（后端按正文自动抽取，也可显式传入） */
  templateVariables?: string[]
  templateDefault?: boolean
  templateEnabled?: boolean
  templateSortOrder?: number
  templateCreatedAt?: number
  templateUpdatedAt?: number
}

/** 模板渲染预览结果 */
export interface MessageTemplateRenderResult {
  ok: boolean
  subject?: string | null
  content: string
  /** 正文引用了但未提供值的变量名 */
  missing?: string[]
}

const TEMPLATE_BASE = '/api/channels/templates'

export const messageTemplateApi = {
  /** GET /api/channels/templates?kind= —— 模板列表（种类可空表示全部） */
  list: (kind?: 'SMS' | 'EMAIL') =>
    unwrapPayload<MessageTemplate[]>(apiHttp.get(TEMPLATE_BASE).query({ kind })),
  /** POST /api/channels/templates —— 新增或更新（带 templateId 即更新） */
  save: (template: MessageTemplate) =>
    unwrapPayload<MessageTemplate>(
      apiHttp.post(TEMPLATE_BASE).data(template as unknown as Record<string, unknown>),
    ),
  /** DELETE /api/channels/templates/{id} */
  remove: (templateId: string) =>
    unwrapPayload<boolean>(apiHttp.delete(`${TEMPLATE_BASE}/${templateId}`)),
  /** POST /api/channels/templates/{id}/default —— 设为该种类默认模板 */
  setDefault: (templateId: string) =>
    unwrapPayload<MessageTemplate>(apiHttp.post(`${TEMPLATE_BASE}/${templateId}/default`)),
  /** POST /api/channels/templates/render —— 变量渲染预览（不落库） */
  render: (body: {
    kind: 'SMS' | 'EMAIL'
    subject?: string
    content: string
    variables?: Record<string, unknown>
  }) => unwrapPayload<MessageTemplateRenderResult>(apiHttp.post(`${TEMPLATE_BASE}/render`).data(body)),
  /** GET /api/channels/templates/variables?kind= —— 模板内置变量清单 */
  variables: (kind: 'SMS' | 'EMAIL') =>
    unwrapPayload<string[]>(apiHttp.get(`${TEMPLATE_BASE}/variables`).query({ kind })),
}
