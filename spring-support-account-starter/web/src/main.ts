/**
 * 账号管理应用入口（账号资产 + 模板管理 + 消息模板，前后端同属一个项目）。
 *
 * <p>{@code .routes(路由树)} 即菜单树：路由自动挂到 layout 之下并驱动侧边栏，
 * 无需再手动 registerStaticMenu / addRoute。</p>
 */
import { createContextApp } from '@repo/app';

import { accountRoutes } from './router';

await createContextApp({ appName: 'spring-support-account-starter-web' })
  .routes(accountRoutes as never)
  .mount('#app');
