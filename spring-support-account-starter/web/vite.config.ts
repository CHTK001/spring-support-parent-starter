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

export default createVite({
  appName: 'spring-support-account-starter-web',
  title: '账号管理',
  server: {
    port: 19093,
    strictPort: false,
    proxy: {
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
