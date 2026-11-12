import { realpathSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { createVite } from '../../../vue-support-parent-starter/turbo/vite/createVite.ts';

/**
 * 账号管理应用 Vite 配置（账号资产 + 模板管理 + 消息模板）。
 *
 * <p><b>为什么后端只指一个地址</b>：模板管理已并入
 * {@code spring-support-account-starter}，模板接口 {@code /api/template} 与账号接口
 * {@code /api/accounts} 现在由同一个进程提供（monitor 后端 8082），
 * 不再需要单独的模板实例（原 18170）。</p>
 *
 * <p>需要指向其他实例时用环境变量覆盖：{@code ACCOUNT_API_TARGET=http://host:port pnpm dev}</p>
 */
const accountApiTarget = process.env.ACCOUNT_API_TARGET ?? 'http://127.0.0.1:8082';

/**
 * 代理转发时移除浏览器 Origin。
 *
 * <p>浏览器对 POST 会带上 {@code Origin: http://localhost:19093}，后端 CORS 白名单
 * 未包含开发端口时 Spring 直接回 403 Invalid CORS request，表现为「查询正常、保存 403」。
 * 开发代理与后端同源转发，移除该头即可。</p>
 */
function stripOrigin(proxy: {
  on: (event: string, cb: (req: { removeHeader: (name: string) => void }) => void) => void;
}): void {
  proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
}

/**
 * dev 期放行工作区之外的真实路径。
 *
 * <p>本应用的源码在外后仓（{@code spring-support-account-starter/web}），而 {@code @repo/*}
 * 别名指向 {@code vue-support-parent-starter/packages/**}。vite 对 {@code /@fs/<绝对路径>}
 * 请求做 {@code server.fs.allow} 校验，未放行时直接被 403 —— 表现为页面能开，但某个
 * 动态 import 的分包（如主题 {@code packages/theme/src/themes/koi/framework.ts}）加载失败、
 * 控制台出现 {@code Failed to fetch dynamically imported module}。</p>
 *
 * <p>用 {@code realpathSync} 解掉 worktree 的 junction / 软链，再放行工作区根与
 * node_modules 的真实目录（与 monitor 的接法一致）。</p>
 */
const fsAllow = (() => {
  const real = (p: string): string => {
    try {
      return realpathSync(p);
    } catch {
      return p;
    }
  };
  const cwd = process.cwd();
  const vueRoot = real(join(cwd, '..', '..', '..', 'vue-support-parent-starter'));
  const webNodeModules = real(join(cwd, 'node_modules'));
  const vueNodeModules = real(join(vueRoot, 'node_modules'));
  return [real(cwd), vueRoot, webNodeModules, dirname(webNodeModules), vueNodeModules, dirname(vueNodeModules)];
})();

export default createVite({
  appName: 'spring-support-account-starter-web',
  title: '账号管理',
  server: {
    port: 19093,
    strictPort: false,
    fs: {
      allow: fsAllow,
    },
    proxy: {
      /**
       * 认证与系统设置前缀。
       *
       * <p>本应用沿用框架登录页（滑块 + 表单），前端固定打 {@code /v2/user/login}、
       * {@code /v2/user/me}、{@code /v2/setting/default}。被测后端就是 monitor 8082
       * （同一进程提供账号/模板接口），因此这些前缀必须一起转发，否则登录请求落到
       * vite 自己的 HTML 上，表现为「一直停在登录页」。</p>
       */
      '/v2': {
        target: accountApiTarget,
        changeOrigin: true,
        configure: stripOrigin,
      },
      '/api/accounts': {
        target: accountApiTarget,
        changeOrigin: true,
        configure: stripOrigin,
      },
      '/api/template': {
        target: accountApiTarget,
        changeOrigin: true,
        configure: stripOrigin,
      },
      '/api': {
        target: accountApiTarget,
        changeOrigin: true,
        configure: stripOrigin,
      },
    },
  },
});
