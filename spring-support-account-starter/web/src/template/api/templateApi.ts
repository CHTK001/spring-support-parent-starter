/**
 * 模板库 API 封装
 *
 * 与模板后端服务对接：模板增删改查、模板分页、设计器数据、打印机配置等。
 * 统一走 `@repo/core` 的 `createModuleHttp('template')`，复用框架注入的加密/签名/401/重试拦截器；
 * baseUrl 由 app.yaml 的 `httpConfig.moduleBaseUrl.template` 决定，未配置时回退 `http.baseUrl`。
 * 返回值 Promise 直接 resolve 业务负载（已剥离 AxiosResponse 与 `{code,msg,data}` 信封）。
 *
 * @author CH
 * @since 4.0.0.42
 */

import { createModuleHttp } from "@repo/core"
import type { RequestBody } from '@repo/utils'
import type { TemplateInfo, TemplatePageQuery, PageResult, TemplateDesignerPayload, PrinterConfig, LogRecord, TemplateLogQuery, ProviderInfo, ProviderSyncResult, TemplateSourceDetail } from '../types/template'

/** 模板模块 Http 客户端（懒创建）。 */
/** 模块可能在 createApp() 注入全局 http 之前被静态导入，因此延迟到首次调用时创建。 */
let httpClient: ReturnType<typeof createModuleHttp> | undefined
const http = new Proxy({} as ReturnType<typeof createModuleHttp>, {
  get(_target, prop) {
    httpClient ??= createModuleHttp("template")
    const value = Reflect.get(httpClient as object, prop)
    return typeof value === "function" ? value.bind(httpClient) : value
  }
})

/**
 * 解包业务负载，兼容「已剥离 / 未剥离 AxiosResponse 外壳」两种层级。
 *
 * <p>框架拦截器只负责加密/签名/401/重试，不剥离 AxiosResponse 外壳，
 * 因此 `.then()` 收到的是 `{ status, data: { code, msg, data } }`。
 * 这里统一取出业务信封的 `data` 层；若剥不出 data 层则按原样返回。</p>
 *
 * @param r 请求返回值
 * @returns 业务负载
 */
function unwrap<T>(r: { data?: { data?: T } | T }): T {
  const body = (r?.data ?? r) as { data?: unknown; code?: unknown; msg?: unknown; message?: unknown } | undefined
  const hasCode = !!body && typeof body === "object" && "code" in (body as object)
  if (hasCode) {
    const code = String((body as { code?: unknown }).code ?? "")
    const ok = code === "00000" || code === "0" || code === "200"
    if (!ok) {
      const msg = (body as { msg?: unknown }).msg ?? (body as { message?: unknown }).message ?? `请求失败（${code}）`
      throw new Error(String(msg))
    }
  }
  const looksLikeEnvelope = !!body && typeof body === "object" && "data" in (body as object)
  return ((looksLikeEnvelope ? (body as { data?: unknown }).data : body) ?? body) as T
}

export const templateApi = {
  // ==================== 模板 ====================
  /** 列表（不分页，按条件查询） */
  list: (params?: Partial<TemplatePageQuery>) =>
    http.get("/list").query(params as Record<string, unknown>).then(unwrap) as Promise<TemplateInfo[]>,
  /** 服务端分页（列表页使用） */
  page: (params: TemplatePageQuery) =>
    http.get("/page").query(params as unknown as Record<string, unknown>).then(unwrap) as Promise<PageResult<TemplateInfo>>,
  /** 详情 */
  get: (id: string) => http.get(`/${id}`).then(unwrap) as Promise<TemplateInfo>,
  /**
   * 模板详情（按模板 ID 取信息）
   *
   * <p>比 {@link templateApi.get} 多一层语义：固定模板（三方同步）会带上三方模板 ID、
   * 归属账号、推送渠道与占位符参数，字段对齐 MessagePush 的发送参数。</p>
   */
  detail: (id: string) => http.get(`/${id}/detail`).then(unwrap) as Promise<TemplateSourceDetail>,
  /** 设计器完整数据（info + content） */
  designer: (id: string) =>
    http.get(`/${id}/designer`).then(unwrap) as Promise<TemplateDesignerPayload>,
  /** 新增 */
  add: (info: Record<string, any>) => http.post("/add").data(info).then(unwrap) as Promise<TemplateInfo>,
  /** 更新（含设计描述） */
  update: (id: string, info: Record<string, any>) =>
    http.put(`/${id}`).data(info).then(unwrap) as Promise<TemplateInfo>,
  /** 删除 */
  remove: (id: string) => http.delete(`/${id}`).then(unwrap),

  // ==================== 提供商 ====================
  /** 提供商列表（全部） */
  providerList: () => http.get("/provider/list").then(unwrap) as Promise<ProviderInfo[]>,
  /** 同步单个提供商的模板（返回新增/更新/失败数） */
  syncProvider: (id: string) =>
    http.post(`/provider/${id}/sync`).then(unwrap) as Promise<ProviderSyncResult>,
  /** 新增提供商 */
  addProvider: (info: Record<string, any>) =>
    http.post("/provider/add").data(info).then(unwrap) as Promise<ProviderInfo>,
  /** 更新提供商配置 */
  updateProvider: (id: string, info: Record<string, any>) =>
    http.put(`/provider/${id}`).data(info).then(unwrap) as Promise<ProviderInfo>,
  /** 删除提供商 */
  removeProvider: (id: string) =>
    http.delete(`/provider/${id}`).then(unwrap) as Promise<boolean>,

  // ==================== 打印 ====================
  /** 打印机列表（应用侧 hiprint 连接地址与 Token） */
  printerList: () => http.get("/printer").then(unwrap) as Promise<PrinterConfig[]>,
  /** 自发现打印机（返回已有打印机名，已默认的排在第一条） */
  discoverPrinters: () => http.get("/printer/discover").then(unwrap) as Promise<string[]>,
  /** 设置默认打印机 */
  setDefaultPrinter: (name: string) =>
    http.post(`/printer/default?name=${encodeURIComponent(name)}`).then(unwrap) as Promise<boolean>,
// ==================== 来源同步 ====================
  /** 来源同步器信息（每个来源是否接入 + 库中该来源的模板数） */
  syncSources: () =>
    http.get("/sync/sources").then(unwrap) as Promise<Array<{ source: string; count: number; registered: number }>>,
  /** 全量来源同步（按来源刷新：message / paper） */
  syncAll: () => http.post("/sync").then(unwrap) as Promise<Record<string, number>>,
  /** 按来源同步：message / paper */
  sync: (source: string) => http.post(`/sync/${source}`).then(unwrap) as Promise<Record<string, number>>,
  /** 新增/更新打印机参数（同时设为默认） */
  setPrinter: (printer: Omit<PrinterConfig, 'isDefault'>) => http.post("/printer").data(printer).then(unwrap) as Promise<PrinterConfig>,
  /** 删除打印机 */
  removePrinter: (name: string) =>
    http.delete(`/printer/${encodeURIComponent(name)}`).then(unwrap) as Promise<boolean>,

  // ==================== 调用日志 ====================
  /** 查询调用日志（预览 / 设计 / 打印 归集） */
  logs: (params: TemplateLogQuery) =>
    http.get("/logs").query(params as unknown as Record<string, unknown>).then(unwrap) as Promise<PageResult<LogRecord>>,
  /** 发起打印（收到确认后才请求此接口） */
  print: (id: string, params: Record<string, any>) =>
    http.post(`/${id}/print`).data(params).then(unwrap) as Promise<boolean>,
  /** 记录调用日志（预览 / 设计 / 打印） */
  log: (record: LogRecord) =>
    http.post("/logs").data(record as unknown as RequestBody).then(unwrap) as Promise<boolean>
}

export default templateApi
