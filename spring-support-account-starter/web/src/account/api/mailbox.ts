import { http, unwrapResult } from '@repo/utils';

/**
 * 收 / 发信箱接口封装。
 *
 * <p>后端挂在统一账号下面（{@code /api/accounts/{accountId}/mailbox}），
 * 收信来自收信 SPI、发信走 MessagePush；本文件只做传输层解包，
 * 不缓存任何信件内容，也不碰明文凭据。</p>
 */

/** 统一解包 { success, data } 信封 */
const unwrap = <T>(res: any): T => unwrapResult<T>(res);

/** 账号接口基址 */
const B = '/api/accounts';

/** 信箱文件夹 */
export interface MailboxFolder {
  /** 服务端文件夹名（取信 / 取详情时原样回传） */
  key: string;
  /** 界面展示名 */
  name: string;
  /** 文件夹语义：INBOX / SENT / DRAFT / JUNK / TRASH */
  role?: string;
  /** 邮件总数 */
  total?: number;
  /** 未读数量 */
  unread?: number;
}

/** 信箱消息 */
export interface MailboxMessage {
  /** 消息标识（同一文件夹内唯一） */
  id: string;
  /** 所在文件夹（服务端名） */
  folder?: string;
  /** 主题 */
  subject?: string;
  /** 发件人 */
  from?: string;
  /** 收件人 */
  to?: string;
  /** 抄送 */
  cc?: string;
  /** 发送时间（毫秒） */
  sentAt?: number | null;
  /** 纯文本摘要（列表用） */
  preview?: string;
  /** 正文（详情用；列表为空） */
  content?: string | null;
  /** 正文类型：text / html */
  contentType?: 'text' | 'html';
  /** 是否未读 */
  unread?: boolean;
  /** 是否带附件 */
  hasAttachment?: boolean;
  /** 附件名 */
  attachments?: string[];
}

/** 信箱概览 */
export interface MailboxOverview {
  /** 账号标识 */
  accountId: string;
  /** 账号种类：EMAIL / SMS */
  kind?: string;
  /** 账号展示名 */
  accountName?: string;
  /** 邮箱地址 / 手机号 */
  address?: string;
  /** 是否具备收信能力 */
  supported?: boolean;
  /** 是否具备发信能力 */
  sendable?: boolean;
  /** 收信实现标识，如 email-imap */
  provider?: string;
  /** 不可用原因 */
  message?: string;
  /** 可用文件夹 */
  folders?: MailboxFolder[];
}

/** 取信条件 */
export interface MailboxQuery {
  /** 服务端文件夹名（默认收件箱） */
  folder?: string;
  /** 取多少封（默认 20） */
  limit?: number;
  /** 跳过多少封（分页） */
  offset?: number;
  /** 只看未读 */
  unreadOnly?: boolean;
  /** 主题 / 发件人模糊词 */
  keyword?: string;
}

/** 发信提交体 */
export interface MailboxSendRequest {
  /** 收件人（邮箱地址 / 手机号） */
  to: string;
  /** 抄送（仅邮件） */
  cc?: string[];
  /** 主题（仅邮件） */
  subject?: string;
  /** 正文 */
  content: string;
  /** 正文类型：text / html */
  contentType?: 'text' | 'html';
  /** 模板编码（短信 / 邮件模板） */
  templateCode?: string;
  /** 模板参数 */
  templateParams?: Record<string, string>;
}

/** 发送结果 */
export interface MailboxSendResult {
  success: boolean;
  provider?: string;
  messageId?: string;
  message?: string;
}

/** 信箱概览（能否收 / 发信、可用文件夹） */
export const getMailboxOverview = (accountId: string) =>
  http().get(`${B}/${accountId}/mailbox`).then(r => unwrap<MailboxOverview>(r));

/** 取信列表（不含正文） */
export const listMailboxMessages = (accountId: string, query?: MailboxQuery) =>
  http()
    .get(`${B}/${accountId}/mailbox/messages`)
    .query({
      folder: query?.folder,
      limit: query?.limit,
      offset: query?.offset,
      unreadOnly: query?.unreadOnly,
      keyword: query?.keyword,
    })
    .then(r => unwrap<MailboxMessage[]>(r));

/** 单封详情（含正文） */
export const getMailboxMessage = (accountId: string, messageId: string, folder?: string) =>
  http()
    .get(`${B}/${accountId}/mailbox/messages/${encodeURIComponent(messageId)}`)
    .query({ folder })
    .then(r => unwrap<MailboxMessage>(r));

/** 发信（邮件 / 短信统一入口） */
export const sendMailboxMessage = (accountId: string, payload: MailboxSendRequest) =>
  http()
    .post(`${B}/${accountId}/mailbox/send`)
    .data(payload as unknown as Record<string, unknown>)
    .then(r => unwrap<MailboxSendResult>(r));
