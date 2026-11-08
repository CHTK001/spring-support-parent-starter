/**
 * 账号管理应用入口（账号资产 + 模板管理 + 消息模板，前后端同属一个项目）。
 *
 * <p>{@code .routes(路由树)} 即菜单树：路由自动挂到 layout 之下并驱动侧边栏，
 * 无需再手动 registerStaticMenu / addRoute。</p>
 */
import { createContextApp } from '@repo/app';

import { accountRoutes } from './router';

/**
 * 安装 Re* 组件预设。
 *
 * <p>账号页与模板页大量使用 {@code ReTableColumn / ReRow / ReCol} 这类「随表格声明」
 * 的小组件，它们由 {@code ReKit} 一次性注册；模板设计器还要 {@code ReDrawer / ReDialog}
 * 等。宿主 monitor 用 {@code preset: 'monitor'} 早已装好同一套，此处保持一致，
 * 否则独立运行时控制台会出现 {@code Failed to resolve component: ReTableColumn}，
 * 表格列全部渲染不出来。</p>
 */
await createContextApp({ appName: 'spring-support-account-starter-web' })
  .ui({ preset: 'monitor' })
  .routes(accountRoutes as never)
  .mount('#app');
