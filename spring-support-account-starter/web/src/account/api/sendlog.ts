import { http, unwrapResult } from '@repo/utils';

/**
 * 统一发送日志接口封装。
 *
 * <p>后端挂在账号模块（{@code /api/accounts/send-logs}）：邮件 / 短信每次真实
 * 发送（成功或失败）都会异步落一条通道级日志，记录账号、通道、收件人、
 * 完整内容与上游结果。本文件只做传输层解包，不缓存日志内容。</p>
 *
 * <p>业务侧留痕（验证码日志 / 消息推送日志）仍在各业务表，本日志是通道视角
 * 的发送事实，两边维度互补。</p>
 */

/** 统一解包 { success, data } 信封 */
const unwrap = <T>(res: any): T => unwrapResult<T>(res);

/** 发送日志接口基址 */
const B = '/api/accounts/send-logs';

/** 后端时间字段的可能形状（epoch 毫秒 / ISO 字符串 / LocalDateTime 数组） */
export type BackendTime = string | number | number[] | null;

/** 发送日志（通道级），与后端 {@code SysAccountSendLog} 同名同义 */
export interface SysAccountSendLog {
  /** 日志主键（UUID） */
  sendLogId: string;
  /** 发送时间 */
  sendLogTime?: BackendTime;
  /** 账号种类（EMAIL / SMS） */
  sendLogKind?: string | null;
  /** 发信账号 ID */
  sendLogAccountId?: string | null;
  /** 发信账号名称 */
  sendLogAccountName?: string | null;
  /** 发送通道（email / alibaba-sms / tencent-sms / baidu-sms） */
  sendLogProvider?: string | null;
  /** 收件人（邮箱地址 / 手机号） */
  sendLogTarget?: string | null;
  /** 抄送（仅邮件，逗号分隔） */
  sendLogCc?: string | null;
  /** 主题（邮件） */
  sendLogSubject?: string | null;
  /** 完整正文 */
  sendLogContent?: string | null;
  /** 模板编码 */
  sendLogTemplateCode?: string | null;
  /** 是否成功：{@code true} 成功 / {@code false} 失败 */
  sendLogSuccess?: boolean | null;
  /** 服务商消息标识 */
  sendLogMessageId?: string | null;
  /** 失败原因 */
  sendLogError?: string | null;
  /** 耗时（毫秒） */
  sendLogCostMs?: number | null;
  /** 调用来源（mailbox / system:sms-code / system:message-push） */
  sendLogSource?: string | null;
  /** 写入时间 */
  sendLogCreatedAt?: BackendTime;
}

/** 发送日志查询条件 */
export interface SendLogQuery {
  /** 账号种类（EMAIL / SMS，可空） */
  kind?: string;
  /** 发送结果（{@code true} 成功 / {@code false} 失败，可空表示全部） */
  success?: boolean;
  /** 发信账号 ID（可空） */
  accountId?: string;
  /** 调用来源（可空） */
  source?: string;
  /** 收件人模糊匹配（可空） */
  target?: string;
  /** 关键字：主题 / 正文 / 失败原因模糊匹配（可空） */
  keyword?: string;
  /** 发送时间下界（ISO 本地时间，如 2026-11-18T00:00:00，可空） */
  startTime?: string;
  /** 发送时间上界（ISO 本地时间，可空） */
  endTime?: string;
  /** 页码（1 起） */
  page?: number;
  /** 每页条数 */
  pageSize?: number;
}

/** 发送日志分页结果 */
export interface SendLogPage {
  /** 当前页数据 */
  records: SysAccountSendLog[];
  /** 总条数 */
  total: number;
  /** 当前页码 */
  page: number;
  /** 每页条数 */
  pageSize: number;
}

/** 分页查询发送日志（种类 / 结果 / 账号 / 收件人 / 关键字 / 时间范围） */
export const listSendLogs = (query?: SendLogQuery) =>
  http()
    .get(B)
    .query({
      kind: query?.kind,
      success: query?.success,
      accountId: query?.accountId,
      source: query?.source,
      target: query?.target,
      keyword: query?.keyword,
      startTime: query?.startTime,
      endTime: query?.endTime,
      page: query?.page,
      pageSize: query?.pageSize,
    })
    .then(r => unwrap<SendLogPage>(r));

/**
 * 清理指定时间之前的发送日志。
 *
 * @param before 截止时间（ISO 本地时间，必填，后端以此防误清全表）
 * @returns 删除条数
 */
export const clearSendLogs = (before: string) =>
  http()
    .delete(B)
    .query({ before })
    .then(r => unwrap<number>(r));
