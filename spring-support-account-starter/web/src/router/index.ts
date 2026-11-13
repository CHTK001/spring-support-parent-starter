/**
 * @account/web 的路由出口。
 *
 * <p>账号管理与模板管理同属一个项目：账号资产页是入口，模板管理 / 消息模板
 * 是它内部的子视图（见 {@code account/views/ScAccountAssets.vue} 的 views 切换）。</p>
 *
 * <p>但模板工作台里的「打印机管理」「调用日志」以及设计器/预览仍然用
 * {@code router.push({ name: 'TemplatePrinter' })} 跳转，所以这些路由必须真实存在。它们一律以
 * {@code showLink: false} + {@code isVisible: '0'} 注册成隐藏路由：可访问、
 * 但不会在侧边栏再冒出一个「模板管理」菜单（那正是这次要消灭的重复入口）。</p>
 */
import type { RouteConfigsTable } from '@repo/core';

import { templateRoutes } from '../template/router';

/** 模板子页的隐藏路由（设计器 / 预览 / 打印机 / 调用日志 / 消息模板） */
export const templateChildRoutes: RouteConfigsTable[] = templateRoutes.map((route) => ({
  ...route,
  meta: {
    ...(route.meta ?? {}),
    showLink: false,
    isVisible: '0',
  },
}));

/** 账号管理根路由（独立应用时挂到 layout 下；宿主也可直接引用） */
export const accountRoutes: RouteConfigsTable[] = [
  {
    path: '/account-assets',
    name: 'AccountAssets',
    component: () => import('../account/views/ScAccountAssets.vue'),
    meta: {
      title: '账号管理',
      icon: 'User',
      showLink: true,
      keepAlive: true,
      isVisible: '1',
      rank: 20,
    },
  },
  {
    // 密钥管理：独立的密钥页（只管密钥本身），不是账号资产页的分段。
    // 账号表单引用密钥时按类型从这里挑选。
    path: '/secret-assets',
    name: 'SecretAssets',
    component: () => import('../account/views/ScSecretAssets.vue'),
    meta: {
      title: '密钥管理',
      icon: 'Lock',
      showLink: true,
      keepAlive: true,
      isVisible: '1',
      rank: 21,
    },
  },
  ...templateChildRoutes,
];
