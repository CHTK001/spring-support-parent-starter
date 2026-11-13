import { http, unwrapResult } from '@repo/utils';

/**
 * 后端统一返回结构 { code, data, msg } 解包。
 *
 * <p>账号模块自带 {@code ApiResult}（{@code success/data/code/error}），
 * 与全局 {@code ReturnResult} 是两套信封，但成功判定都靠 {@code success}，
 * 因此可以复用同一个解包器。</p>
 */
const unwrap = <T>(res: any): T => unwrapResult<T>(res);

/** 账号模块接口基址（spring-support-account-starter） */
const B = '/api/accounts';

/**
 * 提交体转换。
 *
 * <p>{@code http().data()} 只要求「可 JSON 序列化的对象」，而这里的接口类型
 * 没有索引签名，直接传会被拒绝；用一个显式转换把契约类型与传输层解耦，
 * 不动接口定义（那些字段名就是后端列名，改不得）。</p>
 */
const body = (payload: object): Record<string, unknown> => payload as unknown as Record<string, unknown>;

/** 账号种类：设备 / 第三方 API 密钥、POP/SMTP 邮箱、短信服务商 */
export type AccountKind = 'API_KEY' | 'EMAIL' | 'SMS';

/** 凭据校验状态 */
export type AccountVerifyStatus = 'UNKNOWN' | 'VALID' | 'INVALID';

/**
 * 统一账号。
 *
 * <p>与后端 {@code SysAccount} 同名同义：敏感值 {@code secret} 只进不出，
 * 读接口返回的永远是 {@code null} + {@code hasSecret}。</p>
 */
export interface SysAccount {
  /** 账号标识（UUID） */
  accountId: string;
  /** 账号编码（业务唯一键） */
  code?: string;
  /** 账号名称 */
  name?: string;
  /** 账号种类 */
  kind?: AccountKind;
  /** 厂商 / 服务商，如 aliyun / smtp / huawei */
  vendor?: string;
  /** 所属渠道标识 */
  channelKey?: string;
  /** 登录名 / 发件邮箱 / 手机号 */
  username?: string;
  /** 明文凭据（仅写入；读接口恒为 null） */
  secret?: string | null;
  /** 服务地址：SMTP/POP 主机、API baseUrl */
  endpoint?: string;
  /** 类型专属配置（端口 / SSL / 默认发件人 / 控制台地址等） */
  config?: Record<string, unknown> | null;
  /** 归属用户，空表示部署级共享 */
  owner?: string | null;
  /** 状态：1 启用 / 0 停用 */
  status?: number;
  /** 是否同种类默认账号 */
  defaultAccount?: boolean;
  /** 校验状态 */
  verifyStatus?: AccountVerifyStatus;
  /** 最近校验时间（毫秒） */
  verifyAt?: number | null;
  /** 最近校验说明 */
  verifyMessage?: string | null;
  /** 是否已配置敏感凭据（读接口回显） */
  hasSecret?: boolean;
  /** 创建时间（毫秒） */
  createdAt?: number;
  /** 更新时间（毫秒） */
  updatedAt?: number;
}

/** 账号种类选项 */
export interface AccountKindOption {
  value: AccountKind;
  label: string;
}

/** 校验结果 */
export interface AccountVerifyResult {
  /** 是否通过 */
  valid: boolean;
  /** 落库后的状态：VALID / INVALID / UNKNOWN */
  status: AccountVerifyStatus;
  /** 人类可读说明 */
  message: string;
}

/** 账号列表查询参数 */
export interface AccountQuery {
  /** 账号种类过滤 */
  kind?: string;
  /** 名称 / 编码 / 用户名模糊词 */
  keyword?: string;
}

/** 受支持的账号种类 */
export const getAccountKinds = () =>
  http().get(`${B}/kinds`).then(r => unwrap<AccountKindOption[]>(r));

/**
 * 账号列表。
 *
 * <p>后端按归属隔离：未登录只看部署级账号，登录后额外包含本人创建的账号。</p>
 */
export const listAccounts = (query?: AccountQuery) =>
  http()
    .get(B)
    .query({ kind: query?.kind, keyword: query?.keyword })
    .then(r => unwrap<SysAccount[]>(r));

/** 账号详情（不含明文凭据） */
export const getAccount = (accountId: string) =>
  http().get(`${B}/${accountId}`).then(r => unwrap<SysAccount>(r));

/** 新增账号（{@code secret} 传明文） */
export const createAccount = (payload: SysAccount) =>
  http().post(B).data(body(payload)).then(r => unwrap<SysAccount>(r));

/**
 * 更新账号。
 *
 * <p>{@code secret} 留空或含 {@code ****} 表示不修改，只有明文才覆盖。</p>
 */
export const updateAccount = (accountId: string, payload: SysAccount) =>
  http().put(`${B}/${accountId}`).data(body(payload)).then(r => unwrap<SysAccount>(r));

/** 删除账号（逻辑删除） */
export const deleteAccount = (accountId: string) =>
  http().delete(`${B}/${accountId}`).then(r => unwrap<boolean>(r));

/** 校验账号凭据（走 AccountVerifier SPI，未安装对应实现时返回 UNKNOWN） */
export const verifyAccount = (accountId: string) =>
  http().post(`${B}/${accountId}/verify`).then(r => unwrap<AccountVerifyResult>(r));

/* ==================== 批量操作 ==================== */

/** 批量操作中的单条失败 */
export interface AccountBatchFailure {
  /** 账号标识 */
  accountId: string;
  /** 失败码（FORBIDDEN / NOT_FOUND / INTERNAL_ERROR…） */
  code: string;
  /** 失败原因 */
  message: string;
}

/**
 * 批量操作结果。
 *
 * <p>后端逐条执行、单条失败不回滚其它条目，因此调用方必须同时看
 * {@code succeeded} 与 {@code failed}：全部成功才是「全部成功」。</p>
 */
export interface AccountBatchResult {
  /** 请求条数（去重后） */
  total: number;
  /** 成功条数 */
  succeeded: number;
  /** 失败条数 */
  failed: number;
  /** 失败明细 */
  failures: AccountBatchFailure[];
}

/** 批量启用 / 停用（{@code status} 1 启用 / 0 停用） */
export const batchUpdateAccountStatus = (accountIds: string[], status: number) =>
  http()
    .post(`${B}/batch/status`)
    .query({ status })
    .data(body({ accountIds }))
    .then(r => unwrap<AccountBatchResult>(r));

/** 批量校验凭据（账号健康巡检，逐条写入校验状态） */
export const batchVerifyAccounts = (accountIds: string[]) =>
  http()
    .post(`${B}/batch/verify`)
    .data(body({ accountIds }))
    .then(r => unwrap<AccountBatchResult>(r));

/**
 * 批量删除（逻辑删除）。
 *
 * <p>用 POST 而不是带请求体的 DELETE：部分网关与客户端会丢掉 DELETE 的请求体。</p>
 */
export const batchDeleteAccounts = (accountIds: string[]) =>
  http()
    .post(`${B}/batch/delete`)
    .data(body({ accountIds }))
    .then(r => unwrap<AccountBatchResult>(r));

/* ==================== 外部访问密钥（AppKey） ==================== */

/**
 * 账号模块的外部访问密钥。
 *
 * <p>与 AI 的模型计费 AppKey 无关，这里给设备 / 脚本 / 外部系统发凭据。</p>
 */
export interface SysAccountAppKey {
  appKeyId: string;
  name?: string;
  /** 可检索的 Key 标识 */
  appKey?: string;
  /** 落库密文，接口不回显 */
  secret?: string | null;
  owner?: string | null;
  status?: number;
  /** 过期时间（毫秒，空表示永不过期） */
  expireAt?: number | null;
  /** IP 白名单（逗号分隔） */
  ipWhitelist?: string | null;
  remark?: string | null;
  lastUsedAt?: number | null;
  createdAt?: number;
  updatedAt?: number;
  /** 创建 / 轮换时回显一次的明文 Secret */
  plainSecret?: string | null;
}

const AK = `${B}/app-keys`;

/** 访问密钥列表 */
export const listAccountAppKeys = (keyword?: string) =>
  http().get(AK).query({ keyword }).then(r => unwrap<SysAccountAppKey[]>(r));

/** 新增访问密钥（响应里带一次明文 plainSecret） */
export const createAccountAppKey = (payload: SysAccountAppKey) =>
  http().post(AK).data(body(payload)).then(r => unwrap<SysAccountAppKey>(r));

/** 更新访问密钥属性（名称 / 状态 / 过期 / 白名单） */
export const updateAccountAppKey = (appKeyId: string, payload: SysAccountAppKey) =>
  http().put(`${AK}/${appKeyId}`).data(body(payload)).then(r => unwrap<SysAccountAppKey>(r));

/** 轮换 Secret（响应里带一次明文 plainSecret） */
export const rotateAccountAppKey = (appKeyId: string) =>
  http().post(`${AK}/${appKeyId}/rotate`).then(r => unwrap<SysAccountAppKey>(r));

/** 删除访问密钥 */
export const deleteAccountAppKey = (appKeyId: string) =>
  http().delete(`${AK}/${appKeyId}`).then(r => unwrap<boolean>(r));

/* ==================== Cookie 池 ==================== */

/** 免登录渠道 / 站点会话的 Cookie */
export interface SysAccountCookie {
  cookieId: string;
  name?: string;
  domain?: string;
  path?: string;
  /** 落库密文，接口不回显 */
  cookieValue?: string | null;
  owner?: string | null;
  status?: number;
  expireAt?: number | null;
  /** 来源：trae / duckduckgo / manual… */
  source?: string | null;
  remark?: string | null;
  /** 是否已配置内容（读接口回显） */
  hasValue?: boolean;
  createdAt?: number;
  updatedAt?: number;
}

const CK = `${B}/cookies`;

/** Cookie 列表 */
export const listAccountCookies = (query?: { domain?: string; source?: string; keyword?: string }) =>
  http()
    .get(CK)
    .query({ domain: query?.domain, source: query?.source, keyword: query?.keyword })
    .then(r => unwrap<SysAccountCookie[]>(r));

/** Cookie 详情（不含内容） */
export const getAccountCookie = (cookieId: string) =>
  http().get(`${CK}/${cookieId}`).then(r => unwrap<SysAccountCookie>(r));

/** 新增 Cookie（{@code cookieValue} 传明文） */
export const createAccountCookie = (payload: SysAccountCookie) =>
  http().post(CK).data(body(payload)).then(r => unwrap<SysAccountCookie>(r));

/** 更新 Cookie（{@code cookieValue} 留空或掩码表示不修改） */
export const updateAccountCookie = (cookieId: string, payload: SysAccountCookie) =>
  http().put(`${CK}/${cookieId}`).data(body(payload)).then(r => unwrap<SysAccountCookie>(r));

/** 删除 Cookie */
export const deleteAccountCookie = (cookieId: string) =>
  http().delete(`${CK}/${cookieId}`).then(r => unwrap<boolean>(r));

/* ==================== 密钥库 ==================== */

/**
 * 账号密钥库里的厂商密钥。
 *
 * <p>账号只引用 {@code secretId}，换密钥时所有引用它的账号一起生效；
 * {@code secret} 读接口不回显，只回 {@code hasSecret}。</p>
 */
export interface SysAccountSecret {
  secretId: string;
  name?: string;
  /** 用途 / 厂商：aliyun / tencent / huawei / custom */
  provider?: string | null;
  /** AccessKeyId（可明文查询） */
  accessKey?: string | null;
  /** AccessKeySecret（落库密文，接口不回显） */
  secret?: string | null;
  region?: string | null;
  endpoint?: string | null;
  owner?: string | null;
  status?: number;
  remark?: string | null;
  /** 是否已配置 Secret（读接口回显） */
  hasSecret?: boolean;
  createdAt?: number;
  updatedAt?: number;
}

const SK = `${B}/secrets`;

/** 密钥列表 */
export const listAccountSecrets = (query?: { provider?: string; keyword?: string }) =>
  http()
    .get(SK)
    .query({ provider: query?.provider, keyword: query?.keyword })
    .then(r => unwrap<SysAccountSecret[]>(r));

/** 密钥详情（不含明文） */
export const getAccountSecret = (secretId: string) =>
  http().get(`${SK}/${secretId}`).then(r => unwrap<SysAccountSecret>(r));

/** 新增密钥（{@code secret} 传明文） */
export const createAccountSecret = (payload: SysAccountSecret) =>
  http().post(SK).data(body(payload)).then(r => unwrap<SysAccountSecret>(r));

/** 更新密钥（{@code secret} 留空或掩码表示不修改） */
export const updateAccountSecret = (secretId: string, payload: SysAccountSecret) =>
  http().put(`${SK}/${secretId}`).data(body(payload)).then(r => unwrap<SysAccountSecret>(r));

/** 删除密钥 */
export const deleteAccountSecret = (secretId: string) =>
  http().delete(`${SK}/${secretId}`).then(r => unwrap<boolean>(r));

/* ==================== 短信模板同步 ==================== */

/** 短信模板同步结果 */
export interface SmsTemplateSyncResult {
  /** 实际使用的短信通道（由账号 vendor 解析） */
  provider: string;
  /** 云端拉到的模板数 */
  fetched: number;
  /** 新增数 */
  created: number;
  /** 更新数 */
  updated: number;
  /** 跳过数（缺内容 / 保存失败） */
  skipped: number;
}

/**
 * 同步指定短信账号对应厂商的模板。
 *
 * <p>不绑定厂商：后端按账号 vendor 解析 MessagePush SPI 的 listTemplates()。</p>
 */
export const syncAccountSmsTemplates = (accountId: string) =>
  http().post(`${B}/${accountId}/sms/templates/sync`).then(r => unwrap<SmsTemplateSyncResult>(r));

/* ==================== 临时邮箱（mail.tm） ==================== */

/** 临时邮箱接口基址 */
const TM = `${B}/temp-mail`;

/**
 * 账号是否为临时邮箱。
 *
 * <p>临时邮箱不新增账号种类，靠 {@code config.tempProvider} 标记；
 * 判断函数只认这个标记，避免把普通 EMAIL 账号误当临时邮箱。</p>
 */
export const isTempMail = (account?: SysAccount | null): boolean =>
  String((account?.config as Record<string, unknown> | null | undefined)?.tempProvider || '') !== '';

/** 临时邮箱租期到期时间（毫秒，0 表示未知） */
export const tempMailExpireAt = (account?: SysAccount | null): number => {
  const raw = (account?.config as Record<string, unknown> | null | undefined)?.expireAt;
  const value = Number(raw);
  return Number.isFinite(value) ? value : 0;
};

/** 领取一个临时邮箱（{@code name} 可空） */
export const claimTempMailbox = (name?: string) =>
  http().post(TM).data(body({ name: name || '' })).then(r => unwrap<SysAccount>(r));

/** 当前用户可见的临时邮箱 */
export const listTempMailboxes = () =>
  http().get(TM).then(r => unwrap<SysAccount[]>(r));

/** 销毁临时邮箱（远端账号 + 本地账号） */
export const destroyTempMailbox = (accountId: string) =>
  http().delete(`${TM}/${accountId}`).then(r => unwrap<boolean>(r));
