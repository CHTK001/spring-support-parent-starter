/**
 * @pages/template —— 模板库共享包
 *
 * 业务应用通过 @pages/template 引入：
 *   import { createContextApp } from "@repo/app";
 *   import { registerStaticMenu } from "@repo/pinia";
 *   import { templateRoutes, templateMenus } from "@pages/template";
 *
 *   registerStaticMenu(templateMenus);
 *   const { context } = await createContextApp({});
 *   templateRoutes.forEach(r => context.router.addRoute("layout", r));
 *
 * 提供的页面：
 *   - TemplateView：模板库（档案轨 + 多页签工作台）
 *   - TemplateDesigner：模板设计器（拖拽式设计 + 打印）
 *   - TemplatePreviewView：只读预览（单模板打印）
 *   - PrinterManage：打印机管理（接入第三方打印机 + 设置默认打印机）
 *   - LogManage：调用日志（预览 / 设计 / 打印，含参数/时间/IP）
 *   - MessageTemplateView：消息模板（短信 / 邮件收信渲染模板，归模板管理域）
 */
export { default as TemplateView } from './views/TemplateView.vue'
export { default as TemplateDesigner } from './views/TemplateDesigner.vue'
export { default as TemplatePreviewView } from './views/TemplatePreviewView.vue'
export { default as PrinterManage } from './views/PrinterManage.vue'
export { default as LogManage } from './views/LogManage.vue'
export { default as MessageTemplateView } from './views/MessageTemplateView.vue'
export { templateRoutes, templateMenus } from './router'
export { templateApi } from './api/templateApi'
export { messageTemplateApi } from './api/messageTemplateApi'
export type { MessageTemplate, MessageTemplateRenderResult } from './api/messageTemplateApi'
export * from './types/template'
