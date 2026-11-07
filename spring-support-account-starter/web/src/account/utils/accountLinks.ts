import type { SysAccount } from '../api/account';

/**
 * 账号「打开邮箱 / 打开短信」的地址解析。
 *
 * <p><b>为什么需要一层映射</b>：账号表只存了服务地址（{@code endpoint}，如
 * {@code smtp.qq.com}、{@code dysms.aliyun.com}），而用户右键想打开的是
 * <b>网页版控制台</b>。两者不是同一个域名，按约定硬拼 {@code https://<endpoint>}
 * 会打开 404。</p>
 *
 * <p>解析顺序：</p>
 * <ol>
 *   <li>{@code config.webUrl}（或 {@code mailUrl}/{@code smsUrl}/{@code consoleUrl}）
 *       —— 用户显式配置优先；</li>
 *   <li>按邮箱域名 / 服务商内置映射；</li>
 *   <li>邮箱：回退到 {@code https://mail.<根域名>}；短信：服务地址本身是 URL 时直接使用；</li>
 *   <li>都解析不出返回空串，菜单项置灰，不弹一个空白页。</li>
 * </ol>
 */

/** 常见邮箱服务商的网页版地址 */
const MAIL_CONSOLE: Array<[string, string]> = [
  ['qq.com', 'https://mail.qq.com/'],
  ['foxmail.com', 'https://mail.qq.com/'],
  ['163.com', 'https://mail.163.com/'],
  ['126.com', 'https://mail.126.com/'],
  ['188.com', 'https://mail.188.com/'],
  ['yeah.net', 'https://mail.yeah.net/'],
  ['sina.com', 'https://mail.sina.com.cn/'],
  ['sina.cn', 'https://mail.sina.com.cn/'],
  ['sohu.com', 'https://mail.sohu.com/'],
  ['139.com', 'https://mail.10086.cn/'],
  ['189.cn', 'https://mail.189.cn/'],
  ['aliyun.com', 'https://mail.aliyun.com/'],
  ['outlook.com', 'https://outlook.live.com/mail/'],
  ['hotmail.com', 'https://outlook.live.com/mail/'],
  ['live.com', 'https://outlook.live.com/mail/'],
  ['msn.com', 'https://outlook.live.com/mail/'],
  ['gmail.com', 'https://mail.google.com/'],
  ['googlemail.com', 'https://mail.google.com/'],
  ['yahoo.com', 'https://mail.yahoo.com/'],
  ['protonmail.com', 'https://mail.proton.me/'],
  ['proton.me', 'https://mail.proton.me/'],
  ['yandex.com', 'https://mail.yandex.com/'],
  ['zoho.com', 'https://mail.zoho.com/'],
  ['icloud.com', 'https://www.icloud.com/mail'],
  ['me.com', 'https://www.icloud.com/mail'],
  ['263.net', 'https://mail.263.net/'],
  ['tom.com', 'https://mail.tom.com/'],
  ['21cn.com', 'https://mail.21cn.com/'],
];

/** 常见短信服务商的控制台地址 */
const SMS_CONSOLE: Array<[string, string]> = [
  ['aliyun', 'https://dysms.console.aliyun.com/'],
  ['ali', 'https://dysms.console.aliyun.com/'],
  ['tencent', 'https://console.cloud.tencent.com/smsv2'],
  ['qcloud', 'https://console.cloud.tencent.com/smsv2'],
  ['huawei', 'https://console.huaweicloud.com/sms/'],
  ['yunpian', 'https://www.yunpian.com/console/'],
  ['submail', 'https://www.mysubmail.com/console/sms'],
  ['twilio', 'https://console.twilio.com/us1/develop/sms/overview'],
  ['253', 'https://www.253.com/'],
  ['juhe', 'https://www.juhe.cn/'],
];

/**
 * 取配置里的显式地址。
 *
 * @param account 账号
 * @returns 配置的地址；未配置返回空串
 */
function configuredUrl(account: SysAccount): string {
  const config = (account.config || {}) as Record<string, unknown>;
  for (const key of ['webUrl', 'mailUrl', 'smsUrl', 'consoleUrl']) {
    const value = config[key];
    if (typeof value === 'string' && /^https?:\/\//i.test(value.trim())) {
      return value.trim();
    }
  }
  return '';
}

/**
 * 去掉协议、端口与常见主机前缀，得到可用于匹配的域名。
 *
 * @param raw 服务地址 / 邮箱地址
 * @returns 小写根域名；解析不出返回空串
 */
function toDomain(raw?: string | null): string {
  const value = (raw || '').trim().toLowerCase();
  if (!value) return '';
  const withoutProtocol = value.replace(/^[a-z]+:\/\//, '');
  const host = withoutProtocol.split('/')[0].split(':')[0];
  return host.replace(/^(smtp|pop|pop3|imap|mail)\./, '');
}

/**
 * 按「包含」匹配映射表。
 *
 * @param domain 域名
 * @param table  映射表
 * @returns 命中的控制台地址；未命中返回空串
 */
function matchConsole(domain: string, table: Array<[string, string]>): string {
  if (!domain) return '';
  for (const [key, url] of table) {
    if (domain === key || domain.endsWith(`.${key}`) || domain.includes(key)) {
      return url;
    }
  }
  return '';
}

/**
 * 邮箱账号的网页版地址。
 *
 * @param account 账号
 * @returns 可打开的地址；无法解析返回空串
 */
export function mailConsoleUrl(account: SysAccount): string {
  const explicit = configuredUrl(account);
  if (explicit) return explicit;
  const fromUser = (account.username || '').split('@')[1];
  const domain = toDomain(fromUser || account.endpoint);
  const matched = matchConsole(domain, MAIL_CONSOLE);
  if (matched) return matched;
  return domain ? `https://mail.${domain}/` : '';
}

/**
 * 短信账号的控制台地址。
 *
 * @param account 账号
 * @returns 可打开的地址；无法解析返回空串
 */
export function smsConsoleUrl(account: SysAccount): string {
  const explicit = configuredUrl(account);
  if (explicit) return explicit;
  const matched = matchConsole(
    [account.vendor, account.endpoint, account.name].filter(Boolean).join(' ').toLowerCase(),
    SMS_CONSOLE,
  );
  if (matched) return matched;
  const endpoint = (account.endpoint || '').trim();
  return /^https?:\/\//i.test(endpoint) ? endpoint : '';
}

/**
 * 账号对应的「打开邮箱 / 打开短信」地址。
 *
 * @param account 账号
 * @returns 可打开的地址；API_KEY 或无法解析时返回空串
 */
export function accountConsoleUrl(account: SysAccount): string {
  if (account.kind === 'EMAIL') return mailConsoleUrl(account);
  if (account.kind === 'SMS') return smsConsoleUrl(account);
  return '';
}
