/**
 * 全局 UMD 模块的类型声明。
 *
 * vue-plugin-hiprint / jquery 以 UMD 包提供（只有 JS 没有 .d.ts），
 * 由此处 `declare module` 把 TS 的模块决议补全，避免 TS7016。
 */

/** 最小 jQuery 接口：选择器调用 + 元素操作，足够 hiprint UMD 使用 */
interface JQueryStatic {
  (selector?: string | any, context?: any): any
  (fn?: () => any): any
  html(html: string): any
  create(html: string): any
  event: any
  parseJSON: (json: string) => any
  each: (obj: any, cb: (index: number, value: any) => any) => any
  ajax: any
  unique: (arr: any[]) => any
  trim: (str: string) => string
  isFunction: (v: unknown) => boolean
  isPlainObject: (v: unknown) => boolean
  makeArray: (arr: any[]) => any[]
  type: (v: unknown) => string
  noop: () => void
  now: () => number
  proxy: (fn: any, context: any) => any
  data: (el: any, name: string, value?: any) => any
  ready: (fn: any) => any
  extend: (...args: any[]) => any
}

/** hiprint 全局对象（由 vue-plugin-hiprint 安装在 window.hiprint） */
interface Hiprint {
  init(settings: Record<string, unknown>): void
  PrintTemplate: new (settings: Record<string, unknown>) => any
  PrintDefaultStorage: any
  PrintQueueManager: any
  getPrinterList(callback?: (list: unknown[]) => void): void
  PrintElementTypeManager?: any
}

declare module 'jquery' {
  /** jQuery 入口（默认导出，window 上同时挂在 $ / window.jQuery） */
  const jQuery: JQueryStatic
  export default jQuery
}

declare module 'vue-plugin-hiprint' {
  /** hiprint 全局对象 */
  const hiprint: Hiprint
  /** 可拖拽元素类型提供者（拖拽区到画布的介质） */
  const defaultElementTypeProvider: {
    new (...args: any[]): any
  }
  export { hiprint, defaultElementTypeProvider }
}

/** window 上由 UMD 包挂的全局（设计器内使用 jquery + hiprint） */
interface Window {
  $: JQueryStatic | undefined
  jQuery: JQueryStatic | undefined
  hiprint: Hiprint | undefined
}
