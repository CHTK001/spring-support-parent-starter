/**
 * @account/web —— 账号管理（含模板管理 / 消息模板）前端能力出口。
 *
 * <p>账号管理与模板管理已合并为同一个项目（{@code spring-support-account-starter}）：
 * 后端 Java 在 {@code src/main/java}，前端源码在 {@code web/}。宿主应用从
 * {@code @account/web} 取页面，不必再各自维护模板库代码。</p>
 */
export * from './template';

export { default as AccountAssetsPage } from './account/views/ScAccountAssets.vue';
export { default as AccountFormDialog } from './account/components/AccountFormDialog.vue';
export { default as AccountAppKeyDialog } from './account/views/components/AppKeyDialog.vue';
export { default as AccountCookieDialog } from './account/views/components/CookieDialog.vue';
export * from './account/api/account';
export { accountConsoleUrl } from './account/utils/accountLinks';
