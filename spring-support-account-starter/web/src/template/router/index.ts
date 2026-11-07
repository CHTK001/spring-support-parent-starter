/**
 * 模板库路由表 + 静态菜单表
 *
 * 路由挂在 layout 之下：
 * - /template              模板库（列表 + 右键菜单 + 多页签工作台）
 * - /template/printer      打印机管理（接入第三方打印机 + 默认打印机）
 * - /template/logs         调用日志（预览 / 设计 / 打印 归集，含参数/时间/IP）
 * - /template/message      消息模板（短信 / 邮件收信渲染模板，归模板管理域）
 * - /template/designer/:id 模板设计器（拖拽式设计 + 打印预览）
 * - /template/preview/:id  模板预览（不含设计器外壳的只读预览）
 *
 * isVisible = "1" 才能在 koi 主题侧边栏中显示。
 */
import type { RouteConfigsTable } from '@repo/core'

export const templateRoutes: RouteConfigsTable[] = [
  {
    path: '/template',
    name: 'TemplateHome',
    component: () => import('../views/TemplateView.vue'),
    meta: {
      title: '模板库',
      icon: 'MagicStick',
      showLink: true,
      keepAlive: true,
      isVisible: '1',
      rank: 21
    }
  },
  {
    path: '/template/printer',
    name: 'TemplatePrinter',
    component: () => import('../views/PrinterManage.vue'),
    props: true,
    meta: {
      title: '打印机管理',
      icon: 'Printer',
      showLink: true,
      keepAlive: false,
      isVisible: '1',
      rank: 22
    }
  },
  {
    path: '/template/logs',
    name: 'TemplateLogs',
    component: () => import('../views/LogManage.vue'),
    meta: {
      title: '调用日志',
      icon: 'List',
      showLink: true,
      keepAlive: false,
      isVisible: '1',
      rank: 23
    }
  },
  {
    path: '/template/message',
    name: 'TemplateMessage',
    component: () => import('../views/MessageTemplateView.vue'),
    meta: {
      title: '消息模板',
      icon: 'ChatDotRound',
      showLink: true,
      keepAlive: false,
      isVisible: '1',
      rank: 24
    }
  },
  {
    path: '/template/designer/:id',
    name: 'TemplateDesigner',
    component: () => import('../views/TemplateDesigner.vue'),
    props: true,
    meta: {
      title: '模板设计器',
      showLink: false,
      keepAlive: false,
      isVisible: '0'
    }
  },
  {
    path: '/template/preview/:id',
    name: 'TemplatePreview',
    component: () => import('../views/TemplatePreviewView.vue'),
    props: true,
    meta: {
      title: '模板预览',
      showLink: false,
      keepAlive: false,
      isVisible: '0'
    }
  }
]

/**
 * 模板库静态菜单（一级菜单，挂载于“系统管理”下方）
 */
export const templateMenus = [
  {
    path: '/template',
    name: 'TemplateRoot',
    redirect: '/template',
    meta: {
      title: '系统管理',
      icon: 'MagicStick',
      isVisible: '1'
    },
    children: [
      {
        path: '/template',
        name: 'TemplateHome',
        meta: { title: '模板库', icon: 'Monitor', isVisible: '1' }
      },
      {
        path: '/template/printer',
        name: 'TemplatePrinter',
        meta: { title: '打印机管理', icon: 'Printer', isVisible: '1' }
      },
      {
        path: '/template/logs',
        name: 'TemplateLogs',
        meta: { title: '调用日志', icon: 'List', isVisible: '1', rank: 23 }
      },
      {
        path: '/template/message',
        name: 'TemplateMessage',
        meta: { title: '消息模板', icon: 'ChatDotRound', isVisible: '1', rank: 24 }
      }
    ]
  }
]
